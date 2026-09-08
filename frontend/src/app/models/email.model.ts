export type Category =
  | 'ACTION_NEEDED_PAY'
  | 'ACTION_NEEDED_SIGN'
  | 'ACTION_NEEDED_OTHER'
  | 'JOB_INTERVIEWS'
  | 'EVENT'
  | 'JOB_OFFERS'
  | 'PROMOTION'
  | 'NEWSLETTER_TECH'
  | 'NEWSLETTER_OTHER'
  | 'OTHER'
  | 'SPAM';

export const CATEGORIES: Category[] = [
  'ACTION_NEEDED_PAY',
  'ACTION_NEEDED_SIGN',
  'ACTION_NEEDED_OTHER',
  'JOB_INTERVIEWS',
  'EVENT',
  'JOB_OFFERS',
  'PROMOTION',
  'NEWSLETTER_TECH',
  'NEWSLETTER_OTHER',
  'OTHER',
  'SPAM'
];

export interface EmailSummary {
  id: number;
  subject: string | null;
  fromAddress: string | null;
  fromDisplayName: string | null;
  dateSent: string | null;
  category: Category | null;
  summary: string | null;
  confidence: number | null;
  interestScore: number | null;
  dueDate: string | null;
  blendedScore: number | null;
  batchRank: number | null;
  injectionSuspected: boolean;
  needsManualReview: boolean;
  classificationSource: string | null;
}

export interface EmailDetail {
  id: number;
  subject: string | null;
  fromAddress: string | null;
  fromDisplayName: string | null;
  dateSent: string | null;
  category: Category | null;
  summary: string | null;
  confidence: number | null;
  interestScore: number | null;
  dueDate: string | null;
  injectionSuspected: boolean;
  needsManualReview: boolean;
  imagesLoaded: boolean;
  remoteImageCount: number;
  classificationSource: string | null;
  language: string | null;
  messageId: string | null;
}

export interface DualAxisSection {
  byUrgency: EmailSummary[];
  byImportance: EmailSummary[];
  total: number;
}

export interface SummarizedSection {
  top: EmailSummary[];
  total: number;
}

export interface Digest {
  quarantine: EmailSummary[];
  actionNeeded: EmailSummary[];
  events: DualAxisSection;
  jobOffers: DualAxisSection;
  promotions: SummarizedSection;
  newslettersTech: SummarizedSection;
  newslettersOther: SummarizedSection;
  other: SummarizedSection;
  spamCount: number;
  lastJobError: string | null;
}

export interface CategoryPage {
  category: Category;
  total: number;
  offset: number;
  limit: number;
  items: EmailSummary[];
}

export interface TriageJobResult {
  ingested: number;
  skipped: number;
  ingestFailed: number;
  classifiedByRule: number;
  classifiedByLlm: number;
  fallback: number;
  errors: string[];
}

export interface TriageRun {
  id: number;
  startedAt: string;
  finishedAt: string;
  durationMs: number;
  source: string;
  outcome: 'OK' | 'PARTIAL' | 'FAILED';
  ingested: number;
  skipped: number;
  ingestFailed: number;
  requeued: number;
  classifiedByRule: number;
  classifiedByLlm: number;
  fallback: number;
  unclassifiedLeft: number;
  llmReady: boolean;
  errors: string[];
}

export interface SystemStatus {
  mailSource: string;
  imapCredentialsPresent: boolean;
  imapHost: string;
  llmBaseUrl: string;
  llmModel: string | null;
  llmReachable: boolean;
  llmModels: string[];
  llmError: string | null;
}

export function isFixtureSummary(email: EmailSummary): boolean {
  return (email.subject ?? '').startsWith('FAKE TEST DATA');
}

export function withoutFixtures(emails: EmailSummary[]): EmailSummary[] {
  return emails.filter((email) => !isFixtureSummary(email));
}

export function withoutFixtureDigest(digest: Digest): Digest {
  return {
    ...digest,
    quarantine: withoutFixtures(digest.quarantine),
    actionNeeded: withoutFixtures(digest.actionNeeded),
    events: {
      byUrgency: withoutFixtures(digest.events.byUrgency),
      byImportance: withoutFixtures(digest.events.byImportance),
      total: digest.events.total
    },
    jobOffers: {
      byUrgency: withoutFixtures(digest.jobOffers.byUrgency),
      byImportance: withoutFixtures(digest.jobOffers.byImportance),
      total: digest.jobOffers.total
    },
    promotions: {
      top: withoutFixtures(digest.promotions.top),
      total: digest.promotions.total
    },
    newslettersTech: {
      top: withoutFixtures(digest.newslettersTech.top),
      total: digest.newslettersTech.total
    },
    newslettersOther: {
      top: withoutFixtures(digest.newslettersOther.top),
      total: digest.newslettersOther.total
    },
    other: {
      top: withoutFixtures(digest.other.top),
      total: digest.other.total
    }
  };
}

export function categoryLabel(category: Category | null): string {
  if (category === null) {
    return 'Unclassified';
  }
  switch (category) {
    case 'ACTION_NEEDED_PAY':
      return 'Pay';
    case 'ACTION_NEEDED_SIGN':
      return 'Sign';
    case 'ACTION_NEEDED_OTHER':
      return 'Action';
    case 'JOB_INTERVIEWS':
      return 'Interview';
    case 'EVENT':
      return 'Event';
    case 'JOB_OFFERS':
      return 'Job offer';
    case 'PROMOTION':
      return 'Promotion';
    case 'NEWSLETTER_TECH':
      return 'Tech newsletter';
    case 'NEWSLETTER_OTHER':
      return 'Newsletter';
    case 'OTHER':
      return 'Other';
    case 'SPAM':
      return 'Spam';
    default: {
      const exhaustive: never = category;
      return exhaustive;
    }
  }
}
