package com.banking.account_service.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Stand-in for the API Gateway in front of Customer Service and Credit Service.
 *
 * <p>{@code GET /customers/{id}} answers like {@code customer-api.yml}: 200 with the full
 * CustomerResponse (extra fields included) or 404 with code CUSTOMER_NOT_FOUND.
 * {@code GET /credits} answers the call {@code CreditServiceClient} makes: it requires
 * customerId, type=CREDIT_CARD and status=ACTIVE (400 otherwise) and returns a credit page
 * whose totalElements is 1 for a card holder and 0 for any other customer.
 * Faults per route simulate a failing, slow or misrouted dependency.
 */
public final class StubGateway {

    public static final String CUSTOMERS = "/customers";
    public static final String CREDITS = "/credits";

    private final HttpServer server;
    private final Map<UUID, Customer> customers = new ConcurrentHashMap<>();
    private final Set<UUID> cardHolders = ConcurrentHashMap.newKeySet();
    private final Map<String, Fault> faults = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    private StubGateway() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    public static StubGateway start() {
        return new StubGateway();
    }

    public String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** Registers a customer and returns its ID. */
    public UUID addCustomer(String type, String profile, boolean active) {
        UUID id = UUID.randomUUID();
        customers.put(id, new Customer(type, profile, active ? "ACTIVE" : "INACTIVE"));
        return id;
    }

    public void deactivate(UUID customerId) {
        customers.computeIfPresent(customerId, (id, customer) -> new Customer(customer.type(), customer.profile(), "INACTIVE"));
    }

    public void giveCreditCard(UUID customerId) {
        cardHolders.add(customerId);
    }

    /** Every call to the route answers this status with a JSON body without a code. */
    public void fail(String route, int status) {
        faults.put(route, new Fault(status, Duration.ZERO));
    }

    /** Every call to the route waits before answering normally. */
    public void delay(String route, Duration delay) {
        faults.put(route, new Fault(0, delay));
    }

    /** The Gateway has no route: 404 in the Gateway's own format, without CUSTOMER_NOT_FOUND. */
    public void misroute(String route) {
        fail(route, 404);
    }

    public int calls(String route) {
        AtomicInteger count = calls.get(route);
        return count == null ? 0 : count.get();
    }

    public void reset() {
        customers.clear();
        cardHolders.clear();
        faults.clear();
        calls.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String route = path.startsWith(CUSTOMERS) ? CUSTOMERS : path.startsWith(CREDITS) ? CREDITS : path;
            calls.computeIfAbsent(route, key -> new AtomicInteger()).incrementAndGet();

            Fault fault = faults.get(route);
            if (fault != null) {
                sleep(fault.delay());
                if (fault.status() > 0) {
                    respond(exchange, fault.status(), "{\"timestamp\":\"2026-11-16T15:00:00Z\",\"status\":"
                            + fault.status() + ",\"error\":\"Gateway error\",\"path\":\"" + path + "\"}");
                    return;
                }
            }
            if (route.equals(CUSTOMERS)) {
                customer(exchange, path.substring(CUSTOMERS.length() + 1));
            } else if (route.equals(CREDITS)) {
                credits(exchange);
            } else {
                respond(exchange, 404, "{\"status\":404,\"error\":\"Not Found\"}");
            }
        } catch (IOException clientGone) {
            // The caller timed out and closed the connection; nothing to answer.
        }
    }

    private void customer(HttpExchange exchange, String rawId) throws IOException {
        Customer customer = customers.get(UUID.fromString(rawId));
        if (customer == null) {
            respond(exchange, 404, "{\"code\":\"CUSTOMER_NOT_FOUND\",\"message\":\"The customer does not exist.\","
                    + "\"timestamp\":\"2026-11-16T15:00:00Z\",\"path\":\"/customers/" + rawId + "\",\"traceId\":\"stub\"}");
            return;
        }
        respond(exchange, 200, "{\"id\":\"" + rawId + "\",\"type\":\"" + customer.type() + "\",\"profile\":\""
                + customer.profile() + "\",\"documentType\":\"DNI\",\"documentNumber\":\"12345678\","
                + "\"name\":\"Test Customer\",\"status\":\"" + customer.status() + "\","
                + "\"createdAt\":\"2026-01-01T00:00:00Z\",\"updatedAt\":\"2026-01-01T00:00:00Z\"}");
    }

    private void credits(HttpExchange exchange) throws IOException {
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        if (!"CREDIT_CARD".equals(query.get("type")) || !"ACTIVE".equals(query.get("status"))
                || query.get("customerId") == null) {
            respond(exchange, 400, "{\"code\":\"INVALID_REQUEST\"}");
            return;
        }
        UUID customerId = UUID.fromString(query.get("customerId"));
        boolean hasCard = cardHolders.contains(customerId);
        String items = hasCard
                ? "[{\"id\":\"" + UUID.randomUUID() + "\",\"customerId\":\"" + customerId
                + "\",\"type\":\"CREDIT_CARD\",\"status\":\"ACTIVE\"}]"
                : "[]";
        int total = hasCard ? 1 : 0;
        respond(exchange, 200, "{\"items\":" + items + ",\"page\":0,\"size\":1,\"totalElements\":" + total
                + ",\"totalPages\":" + total + "}");
    }

    private static Map<String, String> query(String rawQuery) {
        if (rawQuery == null) {
            return Map.of();
        }
        return Arrays.stream(rawQuery.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "",
                        (first, second) -> first));
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void sleep(Duration delay) {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private record Customer(String type, String profile, String status) {
    }

    private record Fault(int status, Duration delay) {
    }
}
