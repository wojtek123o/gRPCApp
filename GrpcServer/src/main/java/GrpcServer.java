import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GrpcServer {
    private static final String SERVER_IMAGE_FOLDER = "Serwer/";
    private static final List<DataRecord> dataList = new ArrayList<>();

    public static void main(String[] args) {
        int port = 50001;
        System.out.println("Starting gRPC server...");
        Server server = ServerBuilder.forPort(port)
                .addService(new MyServiceImpl())
                .build();
        try {
            server.start();
            System.out.println("...Server started");
            server.awaitTermination();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    static class MyServiceImpl extends ServiceNameGrpc.ServiceNameImplBase {
        private static final int BUF_SIZE = 1024;

        public void saveData(SaveDataRequest request, StreamObserver<SaveDataResponse> responseObserver) {
            System.out.println("...called saveData unaryProcedure - start");
            String id = request.getId();
            String employeename = request.getEmployeename();
            String filename = request.getFilename();
            int age = request.getAge();

            DataRecord newRecord = new DataRecord(id, employeename, filename, age);
            dataList.add(newRecord);

            SaveDataResponse response = SaveDataResponse.newBuilder()
                    .setMessage("Data saved successfully")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            System.out.println("...called saveData unaryProcedure - end");
        }

        public void readData(ReadDataRequest request, StreamObserver<ReadDataResponse> responseObserver) {
            System.out.println("...called readData unaryProcedure - start");

            String idToFind = request.getId();
            DataRecord foundRecord = null;
            for (DataRecord record : dataList) {
                if (record.getId().equals(idToFind)) {
                    foundRecord = record;
                    break;
                }
            }

            if (foundRecord != null) {
                //jeśli rekord został znaleziony, odpowiedź z danymi rekordu
                ReadDataResponse response = ReadDataResponse.newBuilder()
                        .setId(foundRecord.getId())
                        .setEmployeename(foundRecord.getEmployeename())
                        .setFilename(foundRecord.getFilename())
                        .setAge(foundRecord.getAge())
                        .build();
                responseObserver.onNext(response);
            } else {
                //jeśli rekord nie został znaleziony, odpowiedź informującą o braku rekordu
                ReadDataResponse response = ReadDataResponse.newBuilder()
                        .setId("Not found")
                        .build();
                responseObserver.onNext(response);
            }
            responseObserver.onCompleted();
            System.out.println("...called readData unaryProcedure - end");
        }

        public void listAllData(ListAllDataRequest request, StreamObserver<ReadDataResponse> responseObserver) {
            System.out.println("...called listAllData streamProcedure - start");

            for (DataRecord foundRecord : dataList) {
                //sztuczne opóźnienie
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException  e) {
                    e.printStackTrace();
                }
                ReadDataResponse response = ReadDataResponse.newBuilder()
                        .setId(foundRecord.getId())
                        .setEmployeename(foundRecord.getEmployeename())
                        .setFilename(foundRecord.getFilename())
                        .setAge(foundRecord.getAge())
                        .build();
                responseObserver.onNext(response);
            }

            responseObserver.onCompleted();
            System.out.println("...called listAllData unaryProcedure - end");
        }

        public void searchData(SearchDataRequest request, StreamObserver<SearchDataResponse> responseObserver) {
            System.out.println("...called searchData unaryProcedure - start");

            //sztuczne opóznienie aby pokazać asynchroniczność
            try {
                Thread.sleep(3000); // Simulate 3 seconds delay
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            //wyszukiwanie rekordów po imieniu
            int count = 0;
            String searchName = request.getEmployeename();
            for (DataRecord record : dataList) {
                if (record.getEmployeename().equals(searchName)) {
                    count++;
                }
            }

            SearchDataResponse response = SearchDataResponse.newBuilder()
                    .setCount(count)
                    .build();

            //wysyłanie odpowiedzi do klienta
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            System.out.println("...called searchData unaryProcedure - end");
        }

        public void downloadImage(ImageDownloadRequest request, StreamObserver<FileChunk> responseObserver) {
            System.out.println("...called downloadImage from server - start");
            String filename = request.getFilename();
            System.out.println(filename);
            try (InputStream inputStream = Files.newInputStream(Paths.get(SERVER_IMAGE_FOLDER + filename))) {
                byte[] buffer = new byte[BUF_SIZE];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    FileChunk fileChunk = FileChunk.newBuilder()
                            .setFilename(filename)
                            .setNumOfBytes(bytesRead)
                            .setChunk(ByteString.copyFrom(buffer, 0, bytesRead))
                            .build();
                    //wysłanie kawałków chunków do klienta
                    System.out.println("Uploaded chunk to client: FileName - " + filename + ", num_of_bytes - " + bytesRead + ", chunk - " + Arrays.toString(ByteString.copyFrom(buffer, 0, bytesRead).toByteArray()));
                    responseObserver.onNext(fileChunk);
                    Thread.sleep(500);
                }
                responseObserver.onCompleted();
            } catch (IOException | InterruptedException e) {
                System.err.println("No file found: " + e.getMessage());
                String errorMessage = "Error writing file on server: " + e.getMessage();
                responseObserver.onError(new Throwable(errorMessage));
            }

            System.out.println("...called downloadImage from server - end");
        }

        public StreamObserver<FileChunk> uploadImage(StreamObserver<ImageUploadResponse> responseObserver) {
            System.out.println("...called uploadImage to server - start");
            StreamObserver<FileChunk> srvObserver = new StreamObserver<FileChunk>() {
                public void onNext(FileChunk requestChunk) {
                    String employeeId = requestChunk.getEmployeeId();
                    String filename = requestChunk.getFilename();
                    byte[] chunkData = requestChunk.getChunk().toByteArray();
                    int numOfBytes = requestChunk.getNumOfBytes();
                    System.out.println("Received request chunk: FileName - " + filename + ", num_of_bytes - " + numOfBytes + ", chunk - " + Arrays.toString(chunkData));

                    DataRecord foundRecord = null;
                    for (DataRecord record : dataList) {
                        if (record.getId().equals(employeeId)) {
                            foundRecord = record;
                            break;
                        }
                    }

                    if (foundRecord != null) {
                        foundRecord.setFilename(filename);
                    } else {
                        String errorMessage = "Record not found for employeeId: " + employeeId;
                        responseObserver.onError(new RuntimeException(errorMessage));
                        return; // Przerwij dalszą operację
                    }
                    try {
                        File outputFile = new File(SERVER_IMAGE_FOLDER + "kopia-"+ employeeId+ "-" + filename);
                        OutputStream outputStream = new FileOutputStream(outputFile, true);
                        outputStream.write(chunkData, 0, numOfBytes);
                        outputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        String errorMessage = "Error writing file on server: " + e.getMessage();
                        responseObserver.onError(new RuntimeException(errorMessage));
                    }
                }

                public void onCompleted() {
                    ImageUploadResponse response = ImageUploadResponse.newBuilder()
                            .setMessage("Upload successful")
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    System.out.println("...called uploadImage to server - end");
                }

                public void onError(Throwable throwable) {
                    System.err.println("An error occurred: " + throwable.getMessage());
                }
            };
            return srvObserver;
        }

    }
}
