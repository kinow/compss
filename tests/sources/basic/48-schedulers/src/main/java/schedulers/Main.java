package schedulers;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;


public class Main {

    private static final String FILE_NAME = "counterFile_";

    private static final int SLEEP_WAIT_FOR_RUNTIME = 4_000; // ms


    public static void main(String[] args) {
        // Check and get parameters
        if (args.length != 3) {
            System.out.println("[ERROR] Bad number of parameters");
            System.out.println("    Usage: schedulers.Main <taskWidth> <taskDepth> <counterValue>");
            System.exit(-1);
        }
        int taskWidth = Integer.parseInt(args[0]);
        int taskDepth = Integer.parseInt(args[1]);
        int initialValue = Integer.parseInt(args[2]);

        // ------------------------------------------------------------------------
        // Initial sleep
        try {
            Thread.sleep(SLEEP_WAIT_FOR_RUNTIME);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ------------------------------------------------------------------------
        // Initialize files
        for (int i = 0; i < taskWidth; ++i) {
            String counterName = FILE_NAME + i;

            System.out.println("[INFO] Creating task " + i + " on file " + counterName + " with value " + initialValue);
            try {
                FileOutputStream fos = new FileOutputStream(counterName);
                fos.write(initialValue);
                fos.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
                System.exit(-1);
            }
        }

        // ------------------------------------------------------------------------
        // Execute increment tasks
        for (int depth = 0; depth < taskDepth; ++depth) {
            for (int i = 0; i < taskWidth; ++i) {
                String counterName = FILE_NAME + i;
                MainImpl.increment(counterName);
            }
        }

        // ------------------------------------------------------------------------
        // Synchronize and read final value
        for (int i = 0; i < taskWidth; ++i) {
            String counterName = FILE_NAME + i;
            try {
                FileInputStream fis = new FileInputStream(counterName);
                System.out.println("[INFO] Final counter value on file " + counterName + " is " + fis.read());
                fis.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
                System.exit(-1);
            }
        }
    }

}
