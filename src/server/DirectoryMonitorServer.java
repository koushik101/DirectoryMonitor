package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DirectoryMonitorServer {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.exit(1);
        }

        // Load configuration
        Properties config = new Properties();
        try (InputStream input = new FileInputStream(args[0])) {
            config.load(input);
        }

        String outputDirectory = config.getProperty("output.directory");
        int port = Integer.parseInt(config.getProperty("server.port"));

        Files.createDirectories(Paths.get(outputDirectory));

        ServerSocket serverSocket = new ServerSocket(port);
        ExecutorService executor = Executors.newCachedThreadPool();

        System.out.println("Server listening on port: " + port);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            executor.submit(() -> handleClient(clientSocket, outputDirectory));
        }
    }

    private static void handleClient(Socket socket, String outputDirectory) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            String fileName = (String) in.readObject();
            @SuppressWarnings("unchecked")
            Map<String, String> filteredProperties = (Map<String, String>) in.readObject();

            // Write to file
            Properties properties = new Properties();
            properties.putAll(filteredProperties);

            Path outputFilePath = Paths.get(outputDirectory, fileName);
            try (OutputStream output = new FileOutputStream(outputFilePath.toFile())) {
                properties.store(output, null);
                System.out.println("File written: " + outputFilePath);
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
