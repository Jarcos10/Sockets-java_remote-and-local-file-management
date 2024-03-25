import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Cliente {

    public static void main(String[] args){

        //String dir = "192.168.168.112";
        String dir = "127.0.0.1";
        int port = 7070;

        try {

            Socket socket = new Socket(dir, port);
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            // Obtenemos el directorio home
            String dir_actual = System.getProperty("user.home");

            boolean continuar = true;
            boolean remote = false;
            String terminal;

            System.out.println("Ingresa los comandos (teclea: 'quit' para desconectar 'help' para visualizar los comandos): ");

            while (continuar) {

                System.out.print(dir_actual + "> ");
                terminal = reader.readLine();

                String[] argumentos = terminal.split(" ");
                String comando = argumentos[0].toLowerCase();

                switch (comando) {

                    case "cd":

                        // Remoto
                        if (remote) {

                            dos.writeUTF(terminal);
                            String dir_temporal = dis.readUTF();
                            dos.flush();

                            if (dir_actual.equalsIgnoreCase(dir_temporal))
                                System.out.println("No se encuentra la ruta especificada\n");

                            dir_actual = dir_temporal;
                        }

                        // Local
                        else if (argumentos.length > 1)
                            dir_actual = cambiarDirectorio(argumentos, dir_actual);

                        break;

                    case "cd..":

                        // Remoto
                        if (remote) {

                            dos.writeUTF(terminal);
                            dos.flush();

                            dir_actual = dis.readUTF();
                        }
                        // Local
                        else {
                            File carpeta = new File(dir_actual);
                            dir_actual = carpeta.getParent();
                        }
                        break;

                    case "list":

                        // Remoto
                        if (remote) {
                            dos.writeUTF(terminal);
                            dos.flush();
                            System.out.print(dis.readUTF());
                        }

                        // local
                        else
                            listarCarpetas(dir_actual);

                        break;

                    case "mkdir":

                        // Remoto
                        if (remote) {

                            dos.writeUTF(terminal);
                            dos.flush();

                            System.out.print(dis.readUTF());
                        }
                        // Local
                        else {

                            if (argumentos.length > 1)
                                crearCarpeta(dir_actual, argumentos);
                            else
                                System.out.println("Sintaxis incorrecta del comando\n");
                        }

                        break;

                    case "rmdir":

                        // Remoto
                        if (remote) {

                            dos.writeUTF(terminal);
                            dos.flush();
                            boolean proceso;

                            do {

                                proceso = dis.readBoolean();
                                String respuesta = dis.readUTF();

                                if (respuesta.equalsIgnoreCase("aceptado")) {

                                    System.out.println(dis.readUTF());
                                    Scanner scanner = new Scanner(System.in);
                                    String respuesta2 = scanner.nextLine();

                                    dos.writeUTF(respuesta2);
                                    dos.flush();
                                } else if (respuesta.equalsIgnoreCase("error"))
                                    System.out.println(dis.readUTF());


                            } while (proceso);

                        }

                        // Local
                        else {

                            if (argumentos.length > 1) {
                                eliminarArchivos(dir_actual, argumentos);
                            } else
                                System.out.println("Sintáxis incorrecta del comando\n");
                        }

                        break;

                    case "quit":
                        dos.writeUTF(comando); // Envía comando al servidor
                        continuar = false;
                        break;

                    case "remote":
                        remote = true;
                        dos.writeUTF(comando);
                        dos.flush();
                        dir_actual = dis.readUTF();

                        break;

                    case "local":
                        remote = false;
                        dir_actual = System.getProperty("user.home");
                        break;

                    case "get":

                        dos.writeUTF(terminal);
                        dos.flush();

                        String ruta_original = dir_actual;

                        boolean proceso;
                        do {

                            proceso = dis.readBoolean();
                            String respuesta = dis.readUTF();

                            if (respuesta.equalsIgnoreCase("aceptado")) {

                                File destino = new File(dir_actual);
                                recibirArchivo(dis, dir_actual, true, destino);

                            } else if (respuesta.equalsIgnoreCase("no_aceptado")) {
                                recibirArchivo(dis, dir_actual, false, null);

                            } else if (respuesta.equalsIgnoreCase("error"))
                                System.out.println(dis.readUTF());


                        } while (proceso);

                        dir_actual = ruta_original;

                        break;


                    case "put":

                        dos.writeUTF(comando);
                        dos.flush();

                        if (argumentos.length > 1) {
                            enviarDirectorios(dir_actual, argumentos,dos,dis);
                        } else {

                            System.out.println("Sintáxis incorrecta del comando\n");
                            dos.writeBoolean(false);
                            dos.writeUTF("error");
                        }

                        break;


                    case "help":
                        mostrarAyuda();
                        break;


                    default:
                        dos.writeUTF(comando); // Envía una cadena al servidor
                        dos.flush();
                        System.out.println(dis.readUTF()); // Lee la respuesta del servidor

                        break;
                }
            }

            dos.close();
            dis.close();
            reader.close();
            socket.close();

        } catch (UnknownHostException e) {
            System.err.println("No se reconoce la dirección IP " + dir);
            System.exit(1);

        } catch (IOException e) {
            System.err.println("No se pudieron obtener I/O para la conexión " + dir);
            System.exit(1);
        }
    }



    // Función para mostrar la función de los comandos (help)
    private static void mostrarAyuda(){

        System.out.println("\nLISTA DE COMANDOS\n");

        System.out.println("get: Obtener directorios/archivos de la carpeta remota");
        System.out.println("help: Proporciona información de Ayuda para los comandos");
        System.out.println("list: Listar contenido de los directorios");
        System.out.println("local: Cambiar al equipo local");
        System.out.println("mkdir: Crear un directorio");
        System.out.println("put: Enviar directorios/archivos a la carpeta remota");
        System.out.println("quit: Termina el proceso de ejecución entre cliente-servidor");
        System.out.println("remote: Acceder a la carpeta remota en el servidor");
        System.out.println("rmdir: Eliminar directorio/archivo\n");

    }

    // Función que se encarga de cambiar directorios (cd)
    public static String cambiarDirectorio(String[] args, String dir_actual) {

        String path = "";
        for (int i = 1; i < args.length; i++)
            path = path + args[i] + " ";

        path = path.substring(0,path.length()-1);

        // Obtenemos los argumentos para el cambio de directorio
        File carpeta = new File(dir_actual + "\\" + path);

        // Verificamos si es una carpeta
        if (carpeta.isDirectory())
            return dir_actual + "\\" + path;

        else {
            System.out.println("No se encuentra la ruta especificada\n");
            return dir_actual;
        }
    }

    // Función que se encarga de listar los archivos y directorios (list)
    public static void listarCarpetas(String dir_actual){

        File carpeta = new File(dir_actual);
        String[] listado = carpeta.list();

        if (listado == null || listado.length == 0) {
            System.out.println("No hay elementos dentro de la carpeta actual\n");
        }
        else {
            for (String s : listado) {
                System.out.println(s);
            }
        }
    }

    // Función que se encarga de crear un directorio (mkdir)
    public static void crearCarpeta(String dir_actual, String [] carpetas){

        for(int i = 1; i < carpetas.length; i++) {
            File nueva_carpeta = new File(dir_actual + "\\" + carpetas[i]);
            if (!nueva_carpeta.mkdirs())
                System.out.println("Error al crear el directorio\n");

        }
    }

    // Función que se encarga de eliminar un directorio/archivo (rmdir)
    public static void eliminarArchivos(String dir_actual, String [] archivos){ // Agregar recursividad

            for (int i = 1; i < archivos.length; i++) {

                File archivo_eliminado = new File(dir_actual + "\\" + archivos[i]);

                if (archivo_eliminado.isFile())
                    archivo_eliminado.delete();

                else if (archivo_eliminado.isDirectory()) {

                    System.out.println("¿Desea eliminar la carpeta "+archivos[i]+" (S/N)?");
                    Scanner read = new Scanner(System.in);
                    String respuesta = read.nextLine();

                    if("s".equalsIgnoreCase(respuesta)){

                        File [] files = archivo_eliminado.listFiles();

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
                }
                else
                    System.out.println("No se pudo encontrar " + archivo_eliminado.getAbsolutePath()+"\n");

            }

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

    // Función que se encarga de mandar directorios a la carpeta remota (put)
    public static String enviarDirectorios(String dir_actual, String [] archivos, DataOutputStream dos, DataInputStream dis) throws IOException {

        for (int i = 1; i < archivos.length; i++) {

            dos.writeBoolean(true);
            File archivo = new File(dir_actual + "\\" + archivos[i]);


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

                //archivoZip.delete();


            } else {

                dos.writeUTF("error");
                System.out.println("No se pudo encontrar " + dir_actual + "\\" + archivos[i] + "\n");
            }

            dos.flush();

        }

        dos.writeBoolean(false);
        dos.writeUTF("END");
        dos.flush();

        return "";
    }


    // Función que se encarga de enviar los archivos (put)
    private static void enviarArchivo(DataOutputStream dos, File file, String path) throws IOException {

        dos.writeLong(file.length());
        dos.writeUTF(path);

        // Enviamos el archivo
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];

        int bytes_leidos;
        while((bytes_leidos = fis.read(buffer)) > 0)
            dos.write(buffer, 0, bytes_leidos);

        //dos.flush();
        fis.close();

    }

    // Función que se encargan de recibir archivos (put)
    private static void recibirArchivo(DataInputStream dis, String dir_actual, Boolean carpeta, File destino) throws IOException {

        long tam_archivo = dis.readLong();
        String path = dis.readUTF();


        File file = new File(dir_actual+"\\"+path);

        try(FileOutputStream fos = new FileOutputStream(file)){

            byte[] buffer = new byte[4096];
            int bytes_leidos;

            while(tam_archivo > 0 && (bytes_leidos = dis.read(buffer, 0, (int) Math.min(buffer.length, tam_archivo))) > 0){

                fos.write(buffer, 0, bytes_leidos);
                tam_archivo -= bytes_leidos;
            }
        }

        if(carpeta){

            descomprimirZip(file, destino);
            file.delete();
        }
    }


    // Función que se encarga de descomprimir carpetas (get)
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

    // Función que se encarga de agregar los archivos al ZIP (put)
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

    // Función que se encarga de comprimir la carpeta del ZIP (put)
    public static void comprimirCarpeta(File carpeta, File zipSalida) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipSalida))) {
            String pathBase = carpeta.getName();
            agregarArchivoAlZip(zos, carpeta, pathBase);
        }
    }

}
