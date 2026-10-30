package com.lastplate;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main application entry point for LASTPLATE.
 * Initializes the database connection, performs admin bootstrap,
 * and launches the built-in Java HttpServer with complete REST API
 * and static asset handling.
 */
public class Main {

    private static final UserDAO userDAO = new UserDAO();
    private static final FoodDAO foodDAO = new FoodDAO();
    private static final ReservationDAO reservationDAO = new ReservationDAO();
    private static final AdminDAO adminDAO = new AdminDAO();

    private static int serverPort = 8080;
    private static String adminEmail = "admin@lastplate.com";
    private static String adminPassword = "admin123";
    private static String adminName = "System Administrator";

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   LASTPLATE - Campus Food Reservation System    ");
        System.out.println("         Good food. Zero uncertainty.            ");
        System.out.println("=================================================");

        // 1. Load application properties
        loadConfig();

        // 2. Test Database Connection
        System.out.println("[Startup] Testing MySQL database connection...");
        if (!DBConnection.testConnection()) {
            System.err.println("[Startup] FATAL: Unable to connect to MySQL database at: " + DBConnection.getDbUrl());
            System.err.println("[Startup] Please verify MySQL is running and check credentials in config.properties.");
            System.exit(1);
        }
        System.out.println("[Startup] Database connection verified successfully!");

        // 3. Admin Bootstrap
        adminDAO.bootstrapAdmin(adminEmail, adminPassword, adminName);

        // 4. Start HTTP Server
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(serverPort), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); // Efficient virtual threads in Java 25

            // Register contexts
            server.createContext("/api", new ApiHandler());
            server.createContext("/", new StaticFileHandler());

            server.start();
            System.out.println("=================================================");
            System.out.println("   Server is running successfully!               ");
            System.out.println("   Access URL: http://localhost:" + serverPort + "/       ");
            System.out.println("   Demo Student: Register via /register.html     ");
            System.out.println("   Demo Admin:   " + adminEmail + " / " + adminPassword);
            System.out.println("=================================================");
        } catch (IOException e) {
            System.err.println("[Startup] FATAL: Failed to start HTTP server on port " + serverPort + ": " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void loadConfig() {
        Properties props = new Properties();
        File configFile = new File("config.properties");
        if (configFile.exists()) {
            try (InputStream input = new FileInputStream(configFile)) {
                props.load(input);
            } catch (Exception e) {
                System.err.println("[Config] Error loading config.properties: " + e.getMessage());
            }
        }

        if (props.getProperty("server.port") != null) {
            try {
                serverPort = Integer.parseInt(props.getProperty("server.port").trim());
            } catch (NumberFormatException ignored) {}
        }
        if (props.getProperty("admin.default.email") != null) {
            adminEmail = props.getProperty("admin.default.email").trim();
        }
        if (props.getProperty("admin.default.password") != null) {
            adminPassword = props.getProperty("admin.default.password").trim();
        }
        if (props.getProperty("admin.default.name") != null) {
            adminName = props.getProperty("admin.default.name").trim();
        }
    }

    // ==========================================
    // API HANDLER
    // ==========================================
    static class ApiHandler implements HttpHandler {

        private static final Pattern ADMIN_FOOD_ID_PATTERN = Pattern.compile("^/api/admin/foods/(\\d+)$");
        private static final Pattern CANCEL_RES_PATTERN = Pattern.compile("^/api/reservations/(\\d+)/cancel$");
        private static final Pattern APPROVE_RES_PATTERN = Pattern.compile("^/api/admin/reservations/(\\d+)/approve$");
        private static final Pattern REJECT_RES_PATTERN = Pattern.compile("^/api/admin/reservations/(\\d+)/reject$");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath();

            // Handle preflight CORS OPTIONS
            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Session-Token");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            try {
                // 1. AUTHENTICATION ENDPOINTS
                if ("/api/register".equals(path) && "POST".equals(method)) {
                    handleRegister(exchange);
                    return;
                }
                if ("/api/login".equals(path) && "POST".equals(method)) {
                    handleLogin(exchange);
                    return;
                }
                if ("/api/logout".equals(path) && "POST".equals(method)) {
                    handleLogout(exchange);
                    return;
                }
                if ("/api/me".equals(path) && "GET".equals(method)) {
                    handleMe(exchange);
                    return;
                }

                // 2. FOOD & CATEGORY ENDPOINTS (PUBLIC OR STUDENT)
                if ("/api/categories".equals(path) && "GET".equals(method)) {
                    handleGetCategories(exchange);
                    return;
                }
                if ("/api/foods".equals(path) && "GET".equals(method)) {
                    handleGetFoods(exchange);
                    return;
                }

                // 3. STUDENT RESERVATION ENDPOINTS
                if ("/api/reservations".equals(path) && "POST".equals(method)) {
                    handleCreateReservation(exchange);
                    return;
                }
                if ("/api/my-reservations".equals(path) && "GET".equals(method)) {
                    handleGetMyReservations(exchange);
                    return;
                }
                Matcher cancelMatcher = CANCEL_RES_PATTERN.matcher(path);
                if (cancelMatcher.matches() && "PUT".equals(method)) {
                    int resId = Integer.parseInt(cancelMatcher.group(1));
                    handleCancelReservation(exchange, resId);
                    return;
                }
                if ("/api/student/dashboard".equals(path) && "GET".equals(method)) {
                    handleStudentDashboard(exchange);
                    return;
                }

                // 4. ADMIN ENDPOINTS (REQUIRES ADMIN ROLE)
                if (path.startsWith("/api/admin")) {
                    User admin = requireAdmin(exchange);
                    if (admin == null) return; // Response sent by requireAdmin

                    if ("/api/admin/dashboard".equals(path) && "GET".equals(method)) {
                        handleAdminDashboard(exchange);
                        return;
                    }
                    if ("/api/admin/students".equals(path) && "GET".equals(method)) {
                        handleAdminStudents(exchange);
                        return;
                    }
                    if ("/api/admin/reservations".equals(path) && "GET".equals(method)) {
                        handleAdminReservations(exchange);
                        return;
                    }
                    Matcher approveMatcher = APPROVE_RES_PATTERN.matcher(path);
                    if (approveMatcher.matches() && "PUT".equals(method)) {
                        int resId = Integer.parseInt(approveMatcher.group(1));
                        handleApproveReservation(exchange, resId);
                        return;
                    }
                    Matcher rejectMatcher = REJECT_RES_PATTERN.matcher(path);
                    if (rejectMatcher.matches() && "PUT".equals(method)) {
                        int resId = Integer.parseInt(rejectMatcher.group(1));
                        handleRejectReservation(exchange, resId);
                        return;
                    }
                    if ("/api/admin/foods".equals(path) && "POST".equals(method)) {
                        handleAddFood(exchange);
                        return;
                    }
                    Matcher foodIdMatcher = ADMIN_FOOD_ID_PATTERN.matcher(path);
                    if (foodIdMatcher.matches()) {
                        int foodId = Integer.parseInt(foodIdMatcher.group(1));
                        if ("PUT".equals(method)) {
                            handleUpdateFood(exchange, foodId);
                            return;
                        } else if ("DELETE".equals(method)) {
                            handleDeleteFood(exchange, foodId);
                            return;
                        }
                    }
                }

                // Endpoint not matched
                JsonUtil.sendError(exchange, 404, "Endpoint not found: " + method + " " + path);

            } catch (IllegalArgumentException | IllegalStateException e) {
                JsonUtil.sendError(exchange, 400, e.getMessage());
            } catch (SQLException e) {
                System.err.println("[API Error] SQL: " + e.getMessage());
                e.printStackTrace();
                JsonUtil.sendError(exchange, 500, "Database error: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("[API Error] " + e.getMessage());
                e.printStackTrace();
                JsonUtil.sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        // --- AUTH HANDLERS ---
        private void handleRegister(HttpExchange exchange) throws Exception {
            String body = JsonUtil.readBody(exchange);
            JsonObject json = JsonUtil.fromJson(body, JsonObject.class);
            if (json == null) {
                JsonUtil.sendError(exchange, 400, "Request body is required.");
                return;
            }

            String name = json.has("name") ? json.get("name").getAsString() : "";
            String email = json.has("email") ? json.get("email").getAsString() : "";
            String password = json.has("password") ? json.get("password").getAsString() : "";
            String role = (json.has("role")) ? json.get("role").getAsString() : "STUDENT";

            User newUser = userDAO.register(name, email, password, role);
            String token = SessionManager.createSession(newUser);

            exchange.getResponseHeaders().add("Set-Cookie", "LP_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Lax");

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Registration successful!");
            resp.put("token", token);
            resp.put("user", newUser);
            JsonUtil.sendJson(exchange, 200, resp);
        }

        private void handleLogin(HttpExchange exchange) throws Exception {
            String body = JsonUtil.readBody(exchange);
            JsonObject json = JsonUtil.fromJson(body, JsonObject.class);
            if (json == null) {
                JsonUtil.sendError(exchange, 400, "Request body is required.");
                return;
            }

            String email = json.has("email") ? json.get("email").getAsString() : "";
            String password = json.has("password") ? json.get("password").getAsString() : "";

            User user = userDAO.login(email, password);
            if (user == null) {
                JsonUtil.sendError(exchange, 401, "Invalid email or password.");
                return;
            }

            String token = SessionManager.createSession(user);
            exchange.getResponseHeaders().add("Set-Cookie", "LP_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Lax");

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Login successful!");
            resp.put("token", token);
            resp.put("user", user);
            JsonUtil.sendJson(exchange, 200, resp);
        }

        private void handleLogout(HttpExchange exchange) throws IOException {
            String token = SessionManager.extractToken(exchange);
            if (token != null) {
                SessionManager.removeSession(token);
            }
            exchange.getResponseHeaders().add("Set-Cookie", "LP_SESSION=; Path=/; Max-Age=0; HttpOnly");
            JsonUtil.sendSuccess(exchange, "Logged out successfully.", null);
        }

        private void handleMe(HttpExchange exchange) throws IOException {
            User user = SessionManager.authenticateRequest(exchange);
            if (user == null) {
                JsonUtil.sendError(exchange, 401, "Not authenticated.");
                return;
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("authenticated", true);
            resp.put("user", user);
            JsonUtil.sendJson(exchange, 200, resp);
        }

        // --- FOOD & CATEGORIES ---
        private void handleGetCategories(HttpExchange exchange) throws Exception {
            List<Category> categories = foodDAO.getAllCategories();
            JsonUtil.sendJson(exchange, 200, categories);
        }

        private void handleGetFoods(HttpExchange exchange) throws Exception {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String search = query.get("search");
            Integer categoryId = null;
            if (query.get("category") != null) {
                try {
                    categoryId = Integer.parseInt(query.get("category"));
                } catch (NumberFormatException ignored) {}
            }

            List<FoodItem> items = foodDAO.getAllFoodItems(search, categoryId);
            JsonUtil.sendJson(exchange, 200, items);
        }

        // --- RESERVATIONS ---
        private void handleCreateReservation(HttpExchange exchange) throws Exception {
            User user = requireStudent(exchange);
            if (user == null) return;

            String body = JsonUtil.readBody(exchange);
            JsonObject json = JsonUtil.fromJson(body, JsonObject.class);
            if (json == null || !json.has("foodId") || !json.has("quantity")) {
                JsonUtil.sendError(exchange, 400, "foodId and quantity are required.");
                return;
            }

            int foodId = json.get("foodId").getAsInt();
            int quantity = json.get("quantity").getAsInt();

            Reservation res = reservationDAO.createReservation(user.getUserId(), foodId, quantity);
            JsonUtil.sendSuccess(exchange, "Reservation submitted successfully! Awaiting cafeteria approval.", res);
        }

        private void handleGetMyReservations(HttpExchange exchange) throws Exception {
            User user = requireStudent(exchange);
            if (user == null) return;

            List<Reservation> list = reservationDAO.getReservationsByUserId(user.getUserId());
            JsonUtil.sendJson(exchange, 200, list);
        }

        private void handleCancelReservation(HttpExchange exchange, int resId) throws Exception {
            User user = SessionManager.authenticateRequest(exchange);
            if (user == null) {
                JsonUtil.sendError(exchange, 401, "Authentication required.");
                return;
            }

            reservationDAO.cancelReservation(resId, user.getUserId(), user.isAdmin());
            JsonUtil.sendSuccess(exchange, "Reservation #" + resId + " cancelled successfully.", null);
        }

        private void handleStudentDashboard(HttpExchange exchange) throws Exception {
            User user = requireStudent(exchange);
            if (user == null) return;

            Map<String, Object> stats = reservationDAO.getStudentDashboardStats(user.getUserId());
            List<Reservation> recentReservations = reservationDAO.getReservationsByUserId(user.getUserId());
            if (recentReservations.size() > 5) {
                recentReservations = recentReservations.subList(0, 5);
            }
            stats.put("recentReservations", recentReservations);
            stats.put("user", user);

            JsonUtil.sendJson(exchange, 200, stats);
        }

        // --- ADMIN ACTIONS ---
        private void handleAdminDashboard(HttpExchange exchange) throws Exception {
            Map<String, Object> stats = adminDAO.getDashboardStats();
            JsonUtil.sendJson(exchange, 200, stats);
        }

        private void handleAdminStudents(HttpExchange exchange) throws Exception {
            List<Map<String, Object>> students = userDAO.getAllStudentsWithStats();
            JsonUtil.sendJson(exchange, 200, students);
        }

        private void handleAdminReservations(HttpExchange exchange) throws Exception {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String status = query.get("status");
            List<Reservation> list = adminDAO.getAllReservations(status);
            JsonUtil.sendJson(exchange, 200, list);
        }

        private void handleApproveReservation(HttpExchange exchange, int resId) throws Exception {
            adminDAO.approveReservation(resId);
            JsonUtil.sendSuccess(exchange, "Reservation #" + resId + " has been approved and inventory stock deducted.", null);
        }

        private void handleRejectReservation(HttpExchange exchange, int resId) throws Exception {
            adminDAO.rejectReservation(resId);
            JsonUtil.sendSuccess(exchange, "Reservation #" + resId + " has been rejected. Stock was untouched.", null);
        }

        private void handleAddFood(HttpExchange exchange) throws Exception {
            String body = JsonUtil.readBody(exchange);
            FoodItem item = JsonUtil.fromJson(body, FoodItem.class);
            if (item == null) {
                JsonUtil.sendError(exchange, 400, "Food item data is required.");
                return;
            }

            FoodItem created = foodDAO.addFoodItem(item);
            JsonUtil.sendSuccess(exchange, "Food item created successfully!", created);
        }

        private void handleUpdateFood(HttpExchange exchange, int foodId) throws Exception {
            String body = JsonUtil.readBody(exchange);
            FoodItem item = JsonUtil.fromJson(body, FoodItem.class);
            if (item == null) {
                JsonUtil.sendError(exchange, 400, "Food item data is required.");
                return;
            }
            item.setFoodId(foodId);

            boolean updated = foodDAO.updateFoodItem(item);
            if (updated) {
                FoodItem result = foodDAO.getFoodItemById(foodId);
                JsonUtil.sendSuccess(exchange, "Food item updated successfully!", result);
            } else {
                JsonUtil.sendError(exchange, 404, "Food item #" + foodId + " not found.");
            }
        }

        private void handleDeleteFood(HttpExchange exchange, int foodId) throws Exception {
            boolean deleted = foodDAO.deleteFoodItem(foodId);
            if (deleted) {
                JsonUtil.sendSuccess(exchange, "Food item #" + foodId + " deleted successfully.", null);
            } else {
                JsonUtil.sendError(exchange, 404, "Food item #" + foodId + " not found.");
            }
        }

        // --- AUTHORIZATION HELPERS ---
        private User requireStudent(HttpExchange exchange) throws IOException {
            User user = SessionManager.authenticateRequest(exchange);
            if (user == null) {
                JsonUtil.sendError(exchange, 401, "Authentication required. Please log in.");
                return null;
            }
            return user;
        }

        private User requireAdmin(HttpExchange exchange) throws IOException {
            User user = SessionManager.authenticateRequest(exchange);
            if (user == null) {
                JsonUtil.sendError(exchange, 401, "Authentication required. Please log in as Administrator.");
                return null;
            }
            if (!user.isAdmin()) {
                JsonUtil.sendError(exchange, 403, "Access denied: Administrator privileges required.");
                return null;
            }
            return user;
        }

        private Map<String, String> parseQuery(String rawQuery) {
            Map<String, String> result = new HashMap<>();
            if (rawQuery == null || rawQuery.trim().isEmpty()) {
                return result;
            }
            String[] pairs = rawQuery.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length > 0) {
                    String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                    String val = (kv.length > 1) ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                    result.put(key, val);
                }
            }
            return result;
        }
    }

    // ==========================================
    // STATIC FILE HANDLER
    // ==========================================
    static class StaticFileHandler implements HttpHandler {
        private final File publicDir = new File("public");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Sanitize against directory traversal
            path = path.replace("\\", "/");
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.contains("..")) {
                send404(exchange, "Forbidden path");
                return;
            }

            File requestedFile = new File(publicDir, path);
            if (!requestedFile.exists() || requestedFile.isDirectory()) {
                send404(exchange, "File not found: " + path);
                return;
            }

            String mimeType = getMimeType(requestedFile.getName());
            byte[] fileBytes = Files.readAllBytes(requestedFile.toPath());

            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, must-revalidate");
            exchange.sendResponseHeaders(200, fileBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }
        }

        private void send404(HttpExchange exchange, String msg) throws IOException {
            byte[] bytes = ("404 Not Found: " + msg).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(404, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String getMimeType(String filename) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".html")) return "text/html; charset=UTF-8";
            if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".ico")) return "image/x-icon";
            if (lower.endsWith(".woff2")) return "font/woff2";
            if (lower.endsWith(".woff")) return "font/woff";
            return "application/octet-stream";
        }
    }
}

