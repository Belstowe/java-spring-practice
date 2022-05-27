package dns;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PingUtility {
    private final static int packetNum = 1;

    private PingUtility() {
    }

    /**
     * Извлекает значение в строке str по ключу key. Ожидается, что оно следует
     * сразу после знака равно.
     * 
     * @param str Строка, где должен быть ключ
     * @param key Ключ
     * @return Значение по этому ключу
     */
    private static String extractValue(String str, String key) {
        Pattern p = Pattern.compile(key + "[=<]([+-]?([0-9]*[.])?[0-9]+)");
        Matcher match = p.matcher(str);
        match.find();
        return match.group(1);
    }

    /**
     * Используя системную утилиту 'ping', метод измеряет время, которое занимает
     * передача ICMP-пакета от узла до некоторого сервера с доменом ip.
     * При отклонениях от нормы (например, часть пакетов утеряна) метод записывает
     * информацию в поток логов.
     * 
     * @param ip Домен DNS-сервера.
     * @return Монада, которая может содержать результат. При критической ошибке
     *         (нет утилиты 'ping', сервер не найден, тайм-аут со всеми пакетами)
     *         результат не возвращается.
     * @throws IOException          Ошибки при вводе/выводе в систему.
     * @throws InterruptedException Процесс с утилитой 'ping' был прерван.
     */
    public static Optional<Float> check(String ip)
            throws IOException, InterruptedException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String lang = Locale.getDefault().getLanguage();
        String timeKey, sendFail;

        if (isWindows) {
            switch (lang) {
                case "ru":
                    timeKey = "время";
                    sendFail = "не удалось обнаружить узел";
                case "en":
                default:
                    timeKey = "time";
                    sendFail = "could not find host";
            }
        } else {
            timeKey = "time";
            sendFail = "Name or service not known";
        }

        ProcessBuilder pb = new ProcessBuilder("ping", isWindows ? "-n" : "-c", String.valueOf(packetNum), ip);
        Process proc = pb.start();
        BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        int exitSuccess = proc.waitFor();
        if (exitSuccess != 0) {
            System.err.print("[" + ip + "] ");
            System.err.println("Утилита вернула код " + Integer.toString(exitSuccess) + ".");
        }

        String s = null;
        float totalTimeMs = 0.0f;
        int packetsReceived = 0;
        while ((s = stdInput.readLine()) != null) {
            if (s.contains(timeKey + "=") || s.contains(timeKey + "<")) {
                String timeMsString = extractValue(s, timeKey);
                float timeMs = Float.parseFloat(timeMsString);
                totalTimeMs += timeMs;
                packetsReceived++;
            } else if (s.contains(sendFail)) {
                System.err.print("[" + ip + "] ");
                System.err.println("Не удалось найти сервер по адресу '" + ip + "'. Сервер пропущен.");
                return Optional.empty();
            }
        }

        if (packetsReceived == 0) {
            System.err.print("[" + ip + "] ");
            System.err.println("Запросы на сервер '" + ip + "' остались безответными. Сервер пропущен.");
            return Optional.empty();
        } else if (packetsReceived < packetNum) {
            System.err.print("[" + ip + "] ");
            System.err.println(Integer.toString(packetNum - packetsReceived) + " из " + Integer.toString(packetNum)
                    + " пакетов были потеряны.");
        }

        return Optional.of(totalTimeMs / packetsReceived);
    }
}
