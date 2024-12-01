package client;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DirectoryMonitorClient {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.exit(1);
        }


        Properties config = new Properties();
        try (InputStream input = new FileInputStream(args[0])) {
            config.load(input);
        }

        String directoryPath = config.getProperty("directory.path");
        String regex = config.getProperty("key.filter.pattern");
        String serverAddress = config.getProperty("server.address");
        int serverPort = Integer.parseInt(config.getProperty("server.port"));

        Pattern pattern = Pattern.compile(regex);

        WatchService watchService = FileSystems.getDefault().newWatchService();
        Path path = Paths.get(directoryPath);
        path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

        System.out.println("Monitoring directory: " + directoryPath);

        while (true) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    Path fileName = (Path) event.context();
                    Path filePath = path.resolve(fileName);

                    if (fileName.toString().endsWith(".properties")) {
                        System.out.println("Processing file: " + filePath);


                        Properties properties = new Properties();
                        try (InputStream input = new FileInputStream(filePath.toFile())) {
                            properties.load(input);
                        }


                        Map<String, String> filteredProperties = properties.entrySet().stream()
                                .filter(entry -> pattern.matcher((String) entry.getKey()).matches())
                                .collect(Collectors.toMap(
                                        entry -> (String) entry.getKey(),
                                        entry -> (String) entry.getValue()
                                ));


                        try (Socket socket = new Socket(serverAddress, serverPort);
                             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                            out.writeObject(fileName.toString());
                            out.writeObject(filteredProperties);
                        }


                        Files.delete(filePath);
                        System.out.println("File processed and deleted: " + filePath);
                    }
                }
            }

            key.reset();
        }
    }
}
