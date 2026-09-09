package com.emailtool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String dataDir = "./data";
    private String configDir = "../config";
    private String trainingDir = "../results/training";
    private int taxonomyVersion = 2;
    private String promptVersion = "1";
    private int bodyTruncateChars = 8000;
    private int subjectMaxChars = 150;
    private int summaryMaxChars = 200;
    private boolean scheduleEnabled = false;
    private String corsOrigins = "http://localhost:4200";
    private final Mail mail = new Mail();
    private final Llm llm = new Llm();

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getConfigDir() {
        return configDir;
    }

    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }

    public String getTrainingDir() {
        return trainingDir;
    }

    public void setTrainingDir(String trainingDir) {
        this.trainingDir = trainingDir;
    }

    public int getTaxonomyVersion() {
        return taxonomyVersion;
    }

    public void setTaxonomyVersion(int taxonomyVersion) {
        this.taxonomyVersion = taxonomyVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public int getBodyTruncateChars() {
        return bodyTruncateChars;
    }

    public void setBodyTruncateChars(int bodyTruncateChars) {
        this.bodyTruncateChars = bodyTruncateChars;
    }

    public int getSubjectMaxChars() {
        return subjectMaxChars;
    }

    public void setSubjectMaxChars(int subjectMaxChars) {
        this.subjectMaxChars = subjectMaxChars;
    }

    public int getSummaryMaxChars() {
        return summaryMaxChars;
    }

    public void setSummaryMaxChars(int summaryMaxChars) {
        this.summaryMaxChars = summaryMaxChars;
    }

    public boolean isScheduleEnabled() {
        return scheduleEnabled;
    }

    public void setScheduleEnabled(boolean scheduleEnabled) {
        this.scheduleEnabled = scheduleEnabled;
    }

    public String getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(String corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public Mail getMail() {
        return mail;
    }

    public Llm getLlm() {
        return llm;
    }

    public static class Mail {
        private String source = "maildir";
        private String maildir = "../testdata/eml";
        private final Imap imap = new Imap();

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getMaildir() {
            return maildir;
        }

        public void setMaildir(String maildir) {
            this.maildir = maildir;
        }

        public Imap getImap() {
            return imap;
        }
    }

    public static class Imap {
        private String host = "imap.gmail.com";
        private int port = 993;
        private boolean ssl = true;
        private String username = "";
        private String password = "";
        private String folder = "INBOX";
        private int sinceDays = 14;
        private int recentHours = 24;
        private int maxMessages = 100;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isSsl() {
            return ssl;
        }

        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getFolder() {
            return folder;
        }

        public void setFolder(String folder) {
            this.folder = folder;
        }

        public int getSinceDays() {
            return sinceDays;
        }

        public void setSinceDays(int sinceDays) {
            this.sinceDays = sinceDays;
        }

        public int getRecentHours() {
            return recentHours;
        }

        public void setRecentHours(int recentHours) {
            this.recentHours = recentHours;
        }

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }
    }

    public static class Llm {
        private String baseUrl = "http://127.0.0.1:1234/v1";
        private String model = "";
        private String modelKey = "gemma-4-26b-a4b-it";
        private String cli = "";
        private int timeoutSeconds = 180;
        private int loadTimeoutSeconds = 1200;
        private int minFreeVramMib = 18000;
        private boolean autoLoad = true;
        private boolean autoUnload = true;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getModelKey() {
            return modelKey;
        }

        public void setModelKey(String modelKey) {
            this.modelKey = modelKey;
        }

        public String getCli() {
            return cli;
        }

        public void setCli(String cli) {
            this.cli = cli;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getLoadTimeoutSeconds() {
            return loadTimeoutSeconds;
        }

        public void setLoadTimeoutSeconds(int loadTimeoutSeconds) {
            this.loadTimeoutSeconds = loadTimeoutSeconds;
        }

        public int getMinFreeVramMib() {
            return minFreeVramMib;
        }

        public void setMinFreeVramMib(int minFreeVramMib) {
            this.minFreeVramMib = minFreeVramMib;
        }

        public boolean isAutoLoad() {
            return autoLoad;
        }

        public void setAutoLoad(boolean autoLoad) {
            this.autoLoad = autoLoad;
        }

        public boolean isAutoUnload() {
            return autoUnload;
        }

        public void setAutoUnload(boolean autoUnload) {
            this.autoUnload = autoUnload;
        }
    }
}
