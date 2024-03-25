import java.io.*;
import java.net.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Servidor {

    public static void main(String[] args) {

        int port = 7070;

        try{

            ServerSocket server = new ServerSocket(port);
            server.setOption(StandardSocketOptions.SO_REUSEADDR, true);

            System.out.println("Servidor iniciado en el puerto "+server.getLocalPort()+" esperando clientes...");

            try {

                Socket client = server.accept();
                System.out.println("Cliente conectado desde: "+client.getInetAddress()+": "+client.getPort());

                DataInputStream dis = new DataInputStream(client.getInputStream());
                DataOutputStream dos = new DataOutputStream(client.getOutputStream());

                // Creamos la carpeta remota (Corrregir)
                File file = new File("");
                String ruta_absoluta = file.getAbsolutePath();

                File carpeta = new File(ruta_absoluta+"\\remote");
                if(!carpeta.exists()) {
                    carpeta.mkdirs();
                    carpeta.setWritable(true);
                    carpeta.setReadable(true);
                }

                // Generamos una ruta relativa
                String dir_actual = "\\remote";

                boolean continuar = true;

                while (continuar) {

                    System.out.println("Solicitud recibida..\n");

                    String inputLine = dis.readUTF(); // Lee una cadena enviada por el cliente
                    String [] argumentos = inputLine.split(" ");
                    String comando = argumentos[0].toLowerCase();

                    switch (comando) {

                        case "cd":

                            if (argumentos.length > 1) {
                                dir_actual = cambiarDirectorio(argumentos, dir_actual, ruta_absoluta);
                                dos.writeUTF(dir_actual);
                            }
                            else
                                dos.writeUTF(dir_actual);

                            dos.flush();

                            break;

                        case "cd..":

                            File carpeta_ant = new File(dir_actual);

                            if("\\remote".equalsIgnoreCase(dir_actual))
                                dir_actual = "\\remote";
                            else
                                dir_actual = carpeta_ant.getParent();

                            dos.writeUTF(dir_actual);
                            dos.flush();

                            break;

                        case "list":

                            dos.writeUTF(listarCarpetas(dir_actual, ruta_absoluta));
                            dos.flush();

                            break;


                        case "mkdir":

                            if (argumentos.length > 1)
                                dos.writeUTF(crearCarpeta(dir_actual, argumentos, ruta_absoluta));
                            else
                                dos.writeUTF("Sintaxis incorrecta del comando\n");

                            dos.flush();

                            break;

                        case "rmdir":

                            if (argumentos.length > 1)
                                dos.writeUTF(eliminarArchivos(dir_actual, argumentos, ruta_absoluta, dos, dis));
                            else {

                                dos.writeBoolean(false);
                                dos.writeUTF("error");
                                dos.writeUTF("Sintaxis incorrecta del comando\n");
                            }

                            dos.flush();
                            break;

                        case "get":

                            if (argumentos.length > 1) {

                                dos.writeUTF(obtenerDirectorios(dir_actual, argumentos, ruta_absoluta, dos, dis));
                            }
                            else {

                                dos.writeBoolean(false);
                                dos.writeUTF("error");
                                dos.writeUTF("Sintaxis incorrecta del comando\n");
                            }

                            dos.flush();

                            break;

                        case "put":

                            boolean proceso;
                            do {

                                proceso = dis.readBoolean();
                                String respuesta = dis.readUTF();

                                System.out.println(proceso);
                                System.out.println(respuesta);

                                if (respuesta.equalsIgnoreCase("aceptado")) {

                                    System.out.println(dir_actual);
                                    System.out.println(ruta_absoluta);
                                    File destino = new File(ruta_absoluta+dir_actual);
                                    recibirArchivo(dis, ruta_absoluta, dir_actual, true, destino);

                                } else if (respuesta.equalsIgnoreCase("no_aceptado")) {

                                    recibirArchivo(dis, ruta_absoluta, dir_actual, false, null);

                                } else if (respuesta.equalsIgnoreCase("error"))
                                    System.out.println("Error en la solicitud");


                            } while (proceso);

                            break;

                        case "quit":
                            continuar = false;
                            break;

                        case "remote":
                            // Pasamos la ruta al cliente
                            dos.writeUTF(dir_actual);
                            dos.flush();
                            break;

                        default:
                            dos.writeUTF("Echo: " + inputLine); // Envía una cadena al cliente
                            dos.flush();
                            break;
                    }
                }

                dis.close();
                dos.close();
                client.close();

            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    // Funciones auxiliares
    // Función que se encarga de cambiar directorios (cd)
    public static String cambiarDirectorio(String[] args, String dir_actual, String ruta_absoluta){

        String path = "";
        for (int i = 1; i < args.length; i++)
            path = path + args[i] + " ";

        path = path.substring(0,path.length()-1);

        // Obtenemos los argumentos para el cambio de directorio
        File carpeta = new File(ruta_absoluta+dir_actual + "\\" + path);

        // Verificamos si es una carpeta
        if (carpeta.isDirectory())
            return dir_actual + "\\" + path;
        else
            return dir_actual;

    }

    // Función que se encarga de listar los archivos y directorios (list)
    public static String listarCarpetas(String dir_actual, String ruta_absoluta){

        File carpeta = new File(ruta_absoluta+dir_actual);
        String[] listado = carpeta.list();

        if (listado == null || listado.length == 0) {
            return "No hay elementos dentro de la carpeta actual\n\n";
        }
        else {

            String archivos = "";
            for (String s : listado) {
                System.out.println(s);
                archivos += s+"\n";
            }
            return archivos;
        }
    }

    // Función que se encarga de crear un directorio (mkdir)
    public static String crearCarpeta(String dir_actual, String [] carpetas, String ruta_absoluta){

        for(int i = 1; i < carpetas.length; i++) {
            File nueva_carpeta = new File(ruta_absoluta+dir_actual + "\\" + carpetas[i]);
            if (!nueva_carpeta.mkdirs())
                return "Error al crear el directorio\n";

        }

        return "";
    }

    // Función que se encarga de eliminar un directorio/archivo (rmdir)
    public static String eliminarArchivos(String dir_actual, String [] archivos, String ruta_absoluta, DataOutputStream dos, DataInputStream dis) throws IOException {

        for (int i = 1; i < archivos.length; i++) {

            dos.writeBoolean(true);

            File archivo_eliminado = new File(ruta_absoluta+dir_actual + "\\" + archivos[i]);

            if (archivo_eliminado.isFile()) {

                archivo_eliminado.delete();
                dos.writeUTF("no_aceptado");
            }

            else if (archivo_eliminado.isDirectory()) {

                dos.writeUTF("aceptado");
                dos.writeUTF("¿Desea eliminar la carpeta " + archivos[i] + " (S/N)?");

                String respuesta = dis.readUTF();

                if ("s".equalsIgnoreCase(respuesta)) {

                    File[] files = archivo_eliminado.listFiles();

                    if (files != null) {

                        for (File file : files) {

                            if(file.isDirectory())
                                eliminarCarpeta(file.getAbsolutePath());
                            else
                                file.delete();
                        }
                    }

                    // Eliminamos la carpeta
                    archivo_eliminado.delete();
                }

            } else {

                dos.writeUTF("error");
                dos.writeUTF("No se pudo encontrar " + dir_actual + "\\" + archivos[i] + "\n");
            }

            dos.flush();

        }

        dos.writeBoolean(false);
        dos.flush();

        return "";
    }

    // Fución recursiva para eliminar subdirectorios
    private static void eliminarCarpeta(String ruta){

        File carpeta = new File(ruta);
        File [] files = carpeta.listFiles();

        if (files != null) {
            for (File file : files) {

                if(file.isDirectory())
                    eliminarCarpeta(file.getAbsolutePath());
                else
                    file.delete();
            }
        }

        // Eliminamos la carpeta
        carpeta.delete();
    }

    // Función que se encarga de obtener directorios/archivos
    public static String obtenerDirectorios(String dir_actual, String [] archivos, String ruta_absoluta, DataOutputStream dos, DataInputStream dis) throws IOException {


        for (int i = 1; i < archivos.length; i++) {

            dos.writeBoolean(true);

            File archivo = new File(ruta_absoluta+dir_actual + "\\" + archivos[i]);

            if (archivo.isFile()) {
                dos.writeUTF("no_aceptado");
                enviarArchivo(dos,archivo, archivos[i]);
            }

            else if (archivo.isDirectory()) {

                dos.writeUTF("aceptado");

                File carpeta = new File(archivo.getAbsolutePath());
                System.out.println(archivo);
                File archivoZip = new File(archivo.getAbsolutePath()+".zip");
                System.out.println(archivoZip);

                comprimirCarpeta(carpeta, archivoZip);
                enviarArchivo(dos, archivoZip, carpeta.getName()+".zip");

                archivoZip.delete();

            } else {

                dos.writeUTF("error");
                dos.writeUTF("No se pudo encontrar " + dir_actual + "\\" + archivos[i] + "\n");
            }

            dos.flush();

        }

        dos.writeBoolean(false);
        dos.flush();

        return "";
    }


    private static void enviarArchivo(DataOutputStream dos, File file, String path) throws IOException {

        dos.writeLong(file.length());
        dos.writeUTF(path);

        // Enviamos el archivo
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];

        int bytes_leidos;
        while((bytes_leidos = fis.read(buffer)) > 0)
            dos.write(buffer, 0, bytes_leidos);

        fis.close();

    }

    private static void recibirArchivo(DataInputStream dis, String ruta_absoluta, String dir_actual, Boolean carpeta, File destino) throws IOException {

        long tam_archivo = dis.readLong();
        String path = dis.readUTF();
        File file_ = new File(ruta_absoluta+dir_actual+"\\"+path);

        try(FileOutputStream fos = new FileOutputStream(file_)){

            byte[] buffer = new byte[4096];
            int bytes_leidos;

            while(tam_archivo > 0 && (bytes_leidos = dis.read(buffer, 0, (int) Math.min(buffer.length, tam_archivo))) > 0){

                fos.write(buffer, 0, bytes_leidos);
                tam_archivo -= bytes_leidos;
            }
        }

        if(carpeta){

            descomprimirZip(file_, destino);
            file_.delete();
        }
    }

    // Función que se encarga de agregar los archivos al ZIP
    public static void agregarArchivoAlZip(ZipOutputStream zos, File archivo, String nombreBase) throws IOException {

        if (!archivo.exists()) {
            return;
        }

        if (archivo.isDirectory()) {
            if (nombreBase.endsWith("/")) {
                zos.putNextEntry(new ZipEntry(nombreBase));
                zos.closeEntry();
            } else {
                zos.putNextEntry(new ZipEntry(nombreBase + "/"));
                zos.closeEntry();
            }
            File[] hijos = archivo.listFiles();
            if (hijos != null) {
                for (File hijo : hijos) {
                    agregarArchivoAlZip(zos, hijo, nombreBase + "/" + hijo.getName());
                }
            }
        } else {

            try (FileInputStream fis = new FileInputStream(archivo)) {
                ZipEntry zipEntry = new ZipEntry(nombreBase);
                zos.putNextEntry(zipEntry);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, length);
                }
                zos.closeEntry();
            }
        }
    }

    // Función que se encarga de comprimir la carpeta del ZIP
    public static void comprimirCarpeta(File carpeta, File zipSalida) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipSalida))) {
            String pathBase = carpeta.getName();
            agregarArchivoAlZip(zos, carpeta, pathBase);
        }
    }


    // Función que se encarga de descomprimir carpetas (put)
    public static void descomprimirZip(File archivoZip, File destino) throws IOException {

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archivoZip))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File nuevoArchivo = new File(destino, zipEntry.getName());

                // Crear los directorios padres si no existen
                if (zipEntry.isDirectory()) {
                    nuevoArchivo.mkdirs();
                } else {
                    // Asegurar que los directorios padres existan (para archivos en subcarpetas)
                    nuevoArchivo.getParentFile().mkdirs();

                    // Escribir el contenido del archivo
                    try (FileOutputStream fos = new FileOutputStream(nuevoArchivo)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }

                zipEntry = zis.getNextEntry();
            }
        }
    }



}
