package com.emailtool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String dataDir = "./data";
    private String configDir = "../config";
    /** Where scripts/nightly/run-triage.ps1 writes checks.log and last-success.txt. */
    private String nightlyDir = "../results/nightly";
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
    private final Jobs jobs = new Jobs();

    public Jobs getJobs() {
        return jobs;
    }

    /** Job offers found in mail, queued as candidates in letter-writer's Firestore. */
    public static class Jobs {
        private boolean enabled = false;
        /** Candidates sent to letter-writer per rolling 24 hours. */
        private int dailyLimit = 10;
        /** Postings below this interest score (0-100) are never sent. */
        private int minInterest = 60;
        /** Only job mail received this recently is read, and only its postings are sent. */
        private int lookbackHours = 36;
        private int maxBodyChars = 12000;
        private int maxProfileChars = 6000;
        /** Free-text preferences (location, workload, roles to avoid) in the config dir; optional. */
        private String profileFile = "job-profile.md";
        /** Download the page of each posting sent (at most dailyLimit a day), so letter-writer can rate its fit. */
        private boolean fetchPostings = true;
        /**
         * Job boards opened only in the headless browser, as a signed-out visitor, without asking
         * robots.txt (theirs shuts out every bot): one public page per posting sent, never a crawl.
         */
        private java.util.List<String> browserDomains = new java.util.ArrayList<>(java.util.List.of(
                "linkedin.com", "jobs.ch", "jobup.ch", "jobscout24.ch", "indeed.com", "indeed.ch",
                "glassdoor.com", "glassdoor.ch", "xing.com", "monster.com", "monster.ch", "stepstone.de",
                "google.com"));
        /** Chromium-based browser for browserDomains and script-built pages; blank or missing: link only. */
        private String browser = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";
        private String userAgent = "email-tool-job-candidates/1.0 (personal job-alert reader)";
        private final Firestore firestore = new Firestore();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDailyLimit() {
            return dailyLimit;
        }

        public void setDailyLimit(int dailyLimit) {
            this.dailyLimit = dailyLimit;
        }

        public int getMinInterest() {
            return minInterest;
        }

        public void setMinInterest(int minInterest) {
            this.minInterest = minInterest;
        }

        public int getLookbackHours() {
            return lookbackHours;
        }

        public void setLookbackHours(int lookbackHours) {
            this.lookbackHours = lookbackHours;
        }

        public int getMaxBodyChars() {
            return maxBodyChars;
        }

        public void setMaxBodyChars(int maxBodyChars) {
            this.maxBodyChars = maxBodyChars;
        }

        public int getMaxProfileChars() {
            return maxProfileChars;
        }

        public void setMaxProfileChars(int maxProfileChars) {
            this.maxProfileChars = maxProfileChars;
        }

        public String getProfileFile() {
            return profileFile;
        }

        public void setProfileFile(String profileFile) {
            this.profileFile = profileFile;
        }

        public boolean isFetchPostings() {
            return fetchPostings;
        }

        public void setFetchPostings(boolean fetchPostings) {
            this.fetchPostings = fetchPostings;
        }

        public java.util.List<String> getBrowserDomains() {
            return browserDomains;
        }

        public void setBrowserDomains(java.util.List<String> browserDomains) {
            this.browserDomains = browserDomains;
        }

        public String getBrowser() {
            return browser;
        }

        public void setBrowser(String browser) {
            this.browser = browser;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public Firestore getFirestore() {
            return firestore;
        }
    }

    /** letter-writer's Firestore: same project, database and user id its web app and phone use. */
    public static class Firestore {
        private String projectId = "";
        private String databaseId = "letter";
        private String collection = "opportunities";
        /** Google account {@code sub} (the {@code personal_data} document id). */
        private String userId = "";
        /** Service-account JSON; empty uses Application Default Credentials. */
        private String credentialsFile = "";

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getDatabaseId() {
            return databaseId;
        }

        public void setDatabaseId(String databaseId) {
            this.databaseId = databaseId;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getCredentialsFile() {
            return credentialsFile;
        }

        public void setCredentialsFile(String credentialsFile) {
            this.credentialsFile = credentialsFile;
        }
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getNightlyDir() {
        return nightlyDir;
    }

    public void setNightlyDir(String nightlyDir) {
        this.nightlyDir = nightlyDir;
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
        private int minFreeRamMib = 22000;
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

        public int getMinFreeRamMib() {
            return minFreeRamMib;
        }

        public void setMinFreeRamMib(int minFreeRamMib) {
            this.minFreeRamMib = minFreeRamMib;
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
