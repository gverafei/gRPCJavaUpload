package grpcjavaaudio.servidor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import io.grpc.stub.StreamObserver;
import com.google.protobuf.ByteString;

import com.proto.audio.Audio.DataChunkResponse;
import com.proto.audio.Audio.DownloadFileRequest;
import com.proto.audio.AudioServiceGrpc;

public class ServidorImpl extends AudioServiceGrpc.AudioServiceImplBase {

    @Override
    public void downloadAudio(DownloadFileRequest request, StreamObserver<DataChunkResponse> responseObserver) {
        // Obtenemos el nombre del archivo que quiere el cliente
        String archivoNombre = request.getNombre();
        System.out.println("\n\nEnviando el archivo: " + request.getNombre());

        // Abrimos el archivo
        InputStream fileStream = ServidorImpl.class.getResourceAsStream("/uploads/" + archivoNombre);

        // Establecemos una longitud de chunk
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        // Cuenta los bytes leidos para enviar al cliente
        int length;
        try {
            while ((length = fileStream.read(buffer, 0, bufferSize)) != -1) {
                // Se construye la respuesta a enviarle al cliente
                DataChunkResponse respuesta = DataChunkResponse.newBuilder()
                        .setData(ByteString.copyFrom(buffer, 0, length))
                        .build();

                System.out.print(".");

                // En gRPC se utiliza onNext para enviar la respuesta
                responseObserver.onNext(respuesta);
            }
            System.out.println("\nEnvio de datos terminado.");
            // Cierra el stream
            fileStream.close();
        } catch (IOException e) {
            System.out.println("No se pudo obtener el archivo " + archivoNombre);
        }

        // Avisa que se ha terminado
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<DataChunkResponse> uploadAudio(StreamObserver<DownloadFileRequest> responseObserver) {
        return new StreamObserver<DataChunkResponse>() {
            String archivoNombre;
            OutputStream writer;
            @Override
            public void onNext(DataChunkResponse peticion) {
                if (peticion.hasNombre()) {
                    // Recibimos el nombre del archivo
                    archivoNombre = peticion.getNombre();
                    System.out.println("\nRecibiendo el archivo: " + archivoNombre);
                    try {
                        var pathArchivo = Paths.get("app\\src\\main\\resources\\uploads\\" + archivoNombre).toAbsolutePath();
                        writer = Files.newOutputStream(pathArchivo,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException e) {
                    }
                } else if (peticion.hasData()) {
                    try {                        
                        writer.write(peticion.getData().toByteArray());
                    } catch (IOException e) {
                    }
                    System.out.print(".");
                }
            }

            @Override
            public void onError(Throwable t) {
                this.onCompleted();
            }

            @Override
            public void onCompleted() {
                try {
                    writer.close();
                } catch (IOException e) {
                }
                // Al terminar, regresamos el nombre del archivo
                DownloadFileRequest respuesta = DownloadFileRequest.newBuilder().setNombre(archivoNombre).build();
                responseObserver.onNext(respuesta);
                responseObserver.onCompleted();
                System.out.println("\nRecepcion de datos terminada.");
            }
        };
    }
}
