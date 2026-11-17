package bot.wuliang.restart;

import java.io.File;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 旧进程完全退出后再启动应用，避免端口和堆内存重叠。
 */
public final class RestartLauncher {
    public static void main(String[] args) {
        try {
            if (args.length < 5) {
                throw new IllegalArgumentException("Missing restart arguments");
            }
            long parentPid = Long.parseLong(args[0]);
            Instant parentStart = Instant.parse(args[1]);
            File directory = new File(args[2]);
            File log = new File(args[3]);
            String[] command = Arrays.copyOfRange(args, 4, args.length);
            ProcessHandle parent = ProcessHandle.of(parentPid).orElse(null);
            System.out.println("READY");
            System.out.flush();
            // 主进程在收到 READY 后确认交接。未确认就关闭管道时不启动应用。
            if (System.in.read() != 1) {
                return;
            }
            System.in.close();
            System.out.close();
            if (parent != null && parent.isAlive()
                    && parent.info().startInstant().map(parentStart::equals).orElse(true)) {
                parent.onExit().get(120, TimeUnit.SECONDS);
            }
            Process child = new ProcessBuilder(command)
                    .directory(directory)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.to(log))
                    .start();
            child.getOutputStream().close();
            System.err.println(Instant.now() + " Started PID " + child.pid());
        } catch (Exception e) {
            System.err.println(Instant.now() + " Restart handoff failed");
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}