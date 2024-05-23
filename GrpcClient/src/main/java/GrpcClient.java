import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Scanner;

public class GrpcClient {
    private static ServiceNameGrpc.ServiceNameBlockingStub blockingStub;
    private static ServiceNameGrpc.ServiceNameStub nonBlockingStub;
    private static final String CLIENT_IMAGE_FOLDER = "Klient/";
    private static final int BUF_SIZE = 1024;

    public static void main(String[] args) {
        String address = "localhost";
        int port = 50001;

        System.out.println("Running gRPC client...");
        ManagedChannel channel = ManagedChannelBuilder.forAddress(address, port)
                .usePlaintext()
                .build();

        blockingStub = ServiceNameGrpc.newBlockingStub(channel);
        nonBlockingStub = ServiceNameGrpc.newStub(channel);

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nAvailable operations:");
            System.out.println("1. Save Data");
            System.out.println("2. Read Data");
            System.out.println("3. List All Data");
            System.out.println("4. Search Data");
            System.out.println("5. Download Image");
            System.out.println("6. Upload Image");
            System.out.println("7. Exit");
            System.out.print("Enter your choice: ");

            int choice = scanner.nextInt();

            switch (choice) {
                case 1:
                    saveData(scanner);
                    break;
                case 2:
                    readData(scanner);
                    break;
                case 3:
                    listAllData();
                    break;
                case 4:
                    searchData(scanner);
                    break;
                case 5:
                    downloadImage(scanner);
                    break;
                case 6:
                    uploadImage(scanner);
                    break;
                case 7:
                    channel.shutdown();
                    return;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }

    private static void saveData(Scanner scanner) {
        //wykonywane synchronicznie blokująco unary
        System.out.println("...calling saveData unaryProcedure synchronous...");

        scanner.nextLine();
        System.out.print("Enter EmployeeID: ");
        String id = scanner.nextLine();
        System.out.print("Enter EmployeeName: ");
        String name = scanner.nextLine();
        System.out.print("Enter Age: ");
        int age = scanner.nextInt();

        SaveDataRequest request = SaveDataRequest.newBuilder()
                .setId(id)
                .setEmployeename(name)
                .setAge(age)
                .build();

        SaveDataResponse response = blockingStub.saveData(request);
        System.out.println("Response: " + response.getMessage());
        System.out.println("after calling savedData unaryProcedure");
    }

    private static void readData(Scanner scanner) {
        //wykonywane synchronicznie blokująco unary
        System.out.println("...calling readData unaryProcedure synchronous...");

        scanner.nextLine();
        System.out.print("Enter ID: ");
        String id = scanner.nextLine();

        ReadDataRequest request = ReadDataRequest.newBuilder()
                .setId(id)
                .build();

        ReadDataResponse response = blockingStub.readData(request);
        System.out.println("Response: ID - " + response.getId() + ", Name - " + response.getEmployeename() + ", Filename - " + response.getFilename() + ", Age - " + response.getAge());
        System.out.println("after calling readData unaryProcedure");
    }

    private static void listAllData() {
        //wykonywane synchronicznie blokująco stream
        Iterator<ReadDataResponse> iterator = blockingStub.listAllData(ListAllDataRequest.newBuilder().build());

        System.out.println("...calling listAllData streamProcedure synchronous...");
        while (iterator.hasNext()) {
            ReadDataResponse strResponse = iterator.next();
            System.out.println("Response: ID - " + strResponse.getId() + ", Name - " + strResponse.getEmployeename() + ", Filename - " + strResponse.getFilename() + ", Age - " + strResponse.getAge());
        }
        System.out.println("after calling listAllData streamProcedure");
    }

    private static void searchData(Scanner scanner) {
        //wykonywane asynchronicznie nieblokująco unary
        System.out.println("...calling searchData unaryProcedure asynchronous...");

        scanner.nextLine();
        System.out.print("Enter EmployeeName to search: ");
        String name = scanner.nextLine();

        SearchDataRequest request = SearchDataRequest.newBuilder()
                .setEmployeename(name)
                .build();

        nonBlockingStub.searchData(request, new UnaryObs());
        System.out.println("after calling searchData unaryProcedure");

    }

    private static void downloadImage(Scanner scanner) {
        System.out.println("...calling downloadImage streamProcedure asynchronously...");
        scanner.nextLine();
        //pobranie nazwy pliku od użytkownika
        System.out.print("Enter FileName to download: ");
        String filename = scanner.nextLine();

        //obserwator odpowiedzi dla strumienia
        StreamObserver<FileChunk> responseObserver = new StreamObserver<FileChunk>() {
            public void onNext(FileChunk fileChunk) {
                String filename = fileChunk.getFilename();
                byte[] chunkData = fileChunk.getChunk().toByteArray();
                int numOfBytes = fileChunk.getNumOfBytes();
                System.out.println("Received chunk: FileName - " + filename + ", num_of_bytes - " + numOfBytes + ", chunk - " + Arrays.toString(chunkData));

                //zapis odebranych fragmentów pliku do lokalnego pliku
                try {
                    File outputFile = new File(CLIENT_IMAGE_FOLDER +"kopia-"+ filename);
                    OutputStream outputStream = new FileOutputStream(outputFile, true);

                    outputStream.write(chunkData, 0, numOfBytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            public void onError(Throwable t) {
                System.err.println("An error occurred during file download: " + t.getMessage());
            }

            public void onCompleted() {
                System.out.println("File download completed.");
            }
        };

        //wysłanie żądania pobrania pliku do serwera
        ImageDownloadRequest request = ImageDownloadRequest.newBuilder()
                .setFilename(filename)
                .build();
        nonBlockingStub.downloadImage(request, responseObserver);
    }

    private static void uploadImage(Scanner scanner) {
        //wykonywane asynchronicznie nieblokująco stream
        System.out.println("...calling downloadImage streamProcedure asynchronous...");

        scanner.nextLine();
        System.out.print("Enter FileName to upload: ");
        String filename = scanner.nextLine();
        System.out.print("Enter EmployeeID: ");
        String id = scanner.nextLine();

        try (InputStream inputStream = Files.newInputStream(Paths.get(CLIENT_IMAGE_FOLDER + filename))) {
            byte[] buffer = new byte[BUF_SIZE];
            int bytesRead;

            //obserwator odpowiedzi dla strumienia
            StreamObserver<ImageUploadResponse> responseObserver = new StreamObserver<ImageUploadResponse>() {
                @Override
                public void onNext(ImageUploadResponse response) {
                    System.out.println("Upload status: " + response.getMessage());
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("An error occurred during file upload: " + t.getMessage());
                }

                @Override
                public void onCompleted() {
                    System.out.println("Plik został wysłany pomyślnie.");
                }
            };

            //uploadImage z odpowiednim obserwatorem odpowiedzi
            StreamObserver<FileChunk> requestObserver = nonBlockingStub.uploadImage(responseObserver);

            ReadDataRequest request = ReadDataRequest.newBuilder()
                    .setId(id)
                    .build();
            ReadDataResponse response = blockingStub.readData(request);
            if(!response.getId().equals("Not found")) {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    FileChunk fileChunk = FileChunk.newBuilder()
                            .setFilename(filename)
                            .setNumOfBytes(bytesRead)
                            .setChunk(ByteString.copyFrom(buffer, 0, bytesRead))
                            .setEmployeeId(id)
                            .build();
                    //wysyłani kolejnych chunków pliku do serwera
                    System.out.println("Uploaded chunk to server: FileName - " + filename + ", num_of_bytes - " + bytesRead + ", chunk - " + Arrays.toString(ByteString.copyFrom(buffer, 0, bytesRead).toByteArray()));
                    requestObserver.onNext(fileChunk);
                    Thread.sleep(500);
                }
                requestObserver.onCompleted();
            }else {
                String errorMessage = "Error: Record not found for employeeId";
                System.out.println(errorMessage);
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("No file found: " + e.getMessage());
        }
    }


    private static class UnaryObs implements StreamObserver<SearchDataResponse> {
        public void onNext(SearchDataResponse response) {
            System.out.println("-->async searchData unary onNext: Response: Count - " + response.getCount());
        }
        public void onError(Throwable throwable) {
            System.out.println("-->async searchData unary onError");
        }
        public void onCompleted() {
            System.out.println("-->async searchData unary onCompleted");
        }
    }
}
