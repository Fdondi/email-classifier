package com.emailtool.config;

import com.emailtool.classify.LlmClassifier;
import com.emailtool.ingest.MimeParser;
import com.emailtool.jobs.ApplicationLearning;
import com.emailtool.jobs.FirestoreOpportunityQueue;
import com.emailtool.jobs.HeadlessBrowser;
import com.emailtool.jobs.JobApplicationMailRepository;
import com.emailtool.jobs.JobApplicationRepository;
import com.emailtool.jobs.JobCandidateService;
import com.emailtool.jobs.JobMailScanRepository;
import com.emailtool.jobs.JobPostingExtractor;
import com.emailtool.jobs.JobPostingRepository;
import com.emailtool.jobs.OpportunityQueue;
import com.emailtool.jobs.OutcomeMailReader;
import com.emailtool.jobs.PostingPageFetcher;
import com.emailtool.normalize.HiddenContentStripper;
import com.emailtool.persist.EmailRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobsConfig {

    @Bean
    JobPostingExtractor jobPostingExtractor(LlmClassifier llmClassifier, ObjectMapper mapper) {
        return new JobPostingExtractor(llmClassifier, mapper);
    }

    @Bean(destroyMethod = "close")
    FirestoreOpportunityQueue opportunityQueue(AppProperties properties) {
        return new FirestoreOpportunityQueue(properties.getJobs().getFirestore());
    }

    @Bean
    PostingPageFetcher postingPageFetcher(AppProperties properties, HiddenContentStripper stripper) {
        AppProperties.Jobs jobs = properties.getJobs();
        String exe = jobs.getBrowser();
        HeadlessBrowser browser = exe != null && !exe.isBlank() && Files.isRegularFile(Path.of(exe)) ? new HeadlessBrowser(exe) : null;
        return new PostingPageFetcher(jobs.getBrowserDomains(), jobs.getUserAgent(), stripper::toVisibleText, browser);
    }

    @Bean
    OutcomeMailReader outcomeMailReader(LlmClassifier llmClassifier, ObjectMapper mapper) {
        return new OutcomeMailReader(llmClassifier, mapper);
    }

    @Bean
    ApplicationLearning applicationLearning(
            AppProperties properties,
            OpportunityQueue queue,
            JobApplicationRepository applications,
            JobApplicationMailRepository mails,
            JobPostingRepository postings,
            EmailRepository emails,
            OutcomeMailReader reader) {
        return new ApplicationLearning(properties, queue, applications, mails, postings, emails, reader);
    }

    @Bean
    JobCandidateService jobCandidateService(
            AppProperties properties,
            EmailRepository emailRepository,
            JobPostingRepository postingRepository,
            JobMailScanRepository scanRepository,
            MimeParser mimeParser,
            JobPostingExtractor extractor,
            OpportunityQueue queue,
            PostingPageFetcher fetcher,
            ApplicationLearning learning) {
        return new JobCandidateService(
                properties, emailRepository, postingRepository, scanRepository, mimeParser, extractor, queue, fetcher, learning);
    }
}
