import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    private static final int MAX_WIDTH = 75;
    public static void main(String[] args) {
        AtomicBoolean outputLog = new AtomicBoolean(false);
        AtomicReference<File> jobFile = new AtomicReference<>();
        Arrays.stream(args).forEach(e->{
            if(e.startsWith("-j") && e.endsWith(".jobfile")){
                jobFile.set(new File(e.replace("-j", "")));
            }else if(e.startsWith("-a") && e.equals("-aprint")){
                outputLog.set(true);
            }
        });


        if(null == jobFile.get()){
            System.out.println("====================USAGE====================");
            System.out.println("java -jar backuper.jar -j<path to jobfile>");
            System.out.println("=============================================");

            return;
        }

        CoreZip coreZip = new CoreZip(jobFile.get(), outputLog.get());
        int totalFiles = coreZip.amountFiles();
        Thread t1 = new Thread(coreZip);
        t1.setName("World Of Warcraft ");
        t1.start();

        String threadName = t1.getName();
        while(t1.isAlive()){
            printProgress(coreZip.progress(), totalFiles, threadName);
            try{Thread.sleep(1);}catch(InterruptedException ignored){}
        }
        printProgress(100, 100, threadName);

    }

    private static void printProgress(int value, int max, String threadName ){
        int progress = (value * 100) / max;
        int newProgress = (progress * MAX_WIDTH)/100;
        System.out.print(threadName
                + "|" + "=".repeat(Math.max(0, newProgress)) + (newProgress < MAX_WIDTH ? ">" : "=")
                + " ".repeat(Math.max(0, MAX_WIDTH - newProgress)) + "| " + value + "/" + max + "(" + progress + "%)"
                + " " + "\r");
    }
}
