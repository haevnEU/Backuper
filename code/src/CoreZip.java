import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CoreZip implements Runnable{

    private AtomicBoolean outputLog = new AtomicBoolean(false);
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final int ZIP_LEVEL = Deflater.BEST_SPEED;

    private final AtomicBoolean isActive = new AtomicBoolean(true);
    private final AtomicInteger filesProcessed = new AtomicInteger(0);
    private final List<File> files = new ArrayList<>();
    private final AtomicReference<String> currentFile = new AtomicReference<>();
    private int totalFiles = 0;

    String fileName;
    public CoreZip(File jobPath) {
        this(jobPath, false);
    }
    public CoreZip(File jobPath, boolean outputLog){
        this.outputLog.set(outputLog);
        Date date = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        AtomicReference<String> targetPath = new AtomicReference<>("");
        log("Starting job " + jobPath.getName());
        try(BufferedReader bw = new BufferedReader(new FileReader(jobPath))){
            Stream<String> lines = bw.lines();
            lines.forEach(line ->{
                if(!line.startsWith("#")) {
                    if(line.startsWith("target ")){
                        targetPath.set(line.replace("target ", ""));
                    }else{
                        files.add(new File(line));
                    }
                }
            });
        }catch(Exception ex){ex.printStackTrace();}
        Thread t = new Thread(() -> totalFiles = countFiles());
        t.start();
        int i = 0;
        while(t.isAlive()){
            System.out.print("Scanning" + ".".repeat(i)+"\r");
            i++;
            i %= 4;
            try{Thread.sleep(1);}catch(InterruptedException ignored){}
        }
        fileName = targetPath + dateFormat.format(date) + ".zip";
        log(this.toString());
    }

    public String currentFile(){
        return currentFile.get();
    }

    public boolean running(){
        return isActive.get();
    }

    public int progress(){
        return filesProcessed.get();
    }

    public int amountFiles() {
        return totalFiles;
    }

    private int countFiles(){
        return files.stream().mapToInt(this::countFilesInDirectory).sum();
    }

    private int countFilesInDirectory(File directory) {
        int count = 0;
        for (File file : directory.listFiles()) {
            if (file.isFile()) {
                count++;
            }
            if (file.isDirectory()) {
                count += countFilesInDirectory(file);
            }
        }
        return count;
    }


    @Override
    public void run() {
        isActive.set(true);
        log("Start compression");
        long start = System.currentTimeMillis();
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fileName), BUFFER_SIZE);
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.setLevel(ZIP_LEVEL);
            files.forEach(file -> zipFile(file, zos));
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            long duration = System.currentTimeMillis() - start;
            log("Job finished duration: " + duration + "ms.\n");
            isActive.set(false);
        }
    }

    private void zipFile(File file, ZipOutputStream zos){
        zipFile(file, file.getParentFile().getName() + "/" + file.getName(), zos);
    }

    private void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) {
        if (fileToZip.isHidden()) {
            return;
        }
        try {
            if (fileToZip.isDirectory()) {
                if (fileName.endsWith("/")) {
                    zipOut.putNextEntry(new ZipEntry(fileName));
                } else {
                    zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                }
                zipOut.closeEntry();
                File[] children = fileToZip.listFiles();
                Arrays.stream(children).forEach(file -> zipFile(file, fileName + "/" + file.getName(), zipOut));
                return;
            }
            try (FileInputStream fis = new FileInputStream(fileToZip)) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                filesProcessed.getAndIncrement();
                currentFile.set(fileToZip.getName());
                byte[] bytes = new byte[BUFFER_SIZE];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zipOut.write(bytes, 0, length);
                }
            }
        } catch (IOException ignored) {  }
    }

    private final List<String> logList = new ArrayList<>();
    private void log(String message){
        Date date = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        logList.add(dateFormat.format(date) + ": " + message);
        if(outputLog.get()){
            System.out.println(message);
        }
    }

    public List<String> getLogList() {
        return logList;
    }

    @Override
    public String toString() {
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append("Target path: ").append(fileName).append("\n")
                .append("Following directories will be processed\n");
        files.forEach(file->strBuilder.append("-").append(file.getAbsolutePath()).append("\n"));
        strBuilder.append("Total files to process: ").append(totalFiles);
        return strBuilder.toString();
    }
}
