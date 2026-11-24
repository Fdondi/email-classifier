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
  actionNeeded: boolean;
  summary: string | null;
  confidence: number | null;
  interestScore: number | null;
  dueDate: string | null;
  blendedScore: number | null;
  batchRank: number | null;
  ratingUrgency: number | null;
  ratingImportance: number | null;
  injectionSuspected: boolean;
  needsManualReview: boolean;
  classificationSource: string | null;
  gmailUrl: string | null;
}

export interface EmailDetail {
  id: number;
  subject: string | null;
  fromAddress: string | null;
  fromDisplayName: string | null;
  dateSent: string | null;
  category: Category | null;
  actionNeeded: boolean;
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
  userHidden: boolean;
  gmailUrl: string | null;
}

export interface DualAxisSection {
  byUrgency: EmailSummary[];
  byImportance: EmailSummary[];
  allEmails: EmailSummary[];
  total: number;
}

export interface SummarizedSection {
  top: EmailSummary[];
  total: number;
}

export interface Digest {
  quarantine: EmailSummary[];
  actionNeeded: DualAxisSection;
  events: DualAxisSection;
  jobOffers: DualAxisSection;
  promotions: SummarizedSection;
  newslettersTech: SummarizedSection;
  newslettersOther: SummarizedSection;
  other: SummarizedSection;
  spamCount: number;
  hiddenCount: number;
  lastJobError: string | null;
}

export interface CategoryPage {
  category: Category;
  total: number;
  offset: number;
  limit: number;
  items: EmailSummary[];
}

export type TriagePhase =
  | 'IDLE'
  | 'STARTING_LLM'
  | 'DOWNLOADING'
  | 'CLASSIFYING'
  | 'RANKING'
  | 'JOBS'
  | 'DONE'
  | 'FAILED';

export interface TriageProgress {
  running: boolean;
  phase: TriagePhase | string;
  startedAt: string | null;
  finishedAt: string | null;
  ingested: number;
  skipped: number;
  ingestFailed: number;
  classifiedByRule: number;
  classifiedByLlm: number;
  fallback: number;
  toClassify: number;
  errors: string[];
}

/** @deprecated Prefer TriageProgress; kept for call sites that only need final counts. */
export type TriageJobResult = TriageProgress;

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
  buildVersion: string;
  mailSource: string;
  imapCredentialsPresent: boolean;
  imapHost: string;
  llmBaseUrl: string;
  llmModel: string | null;
  llmReachable: boolean;
  llmModels: string[];
  llmError: string | null;
}

export function todayIsoDate(now: Date = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** True when dueDate is a YYYY-MM-DD string strictly before today (local). */
export function isExpiredDueDate(dueDate: string | null, today: string = todayIsoDate()): boolean {
  return dueDate != null && dueDate < today;
}

/** Split a list into past-due rows and everything else, preserving relative order. */
export function partitionExpiredEmails<T extends { dueDate: string | null }>(
  emails: T[],
  today: string = todayIsoDate()
): { expired: T[]; current: T[] } {
  const expired: T[] = [];
  const current: T[] = [];
  for (const email of emails) {
    if (isExpiredDueDate(email.dueDate, today)) {
      expired.push(email);
    } else {
      current.push(email);
    }
  }
  return { expired, current };
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
    actionNeeded: {
      byUrgency: withoutFixtures(digest.actionNeeded.byUrgency),
      byImportance: withoutFixtures(digest.actionNeeded.byImportance),
      allEmails: withoutFixtures(digest.actionNeeded.allEmails ?? []),
      total: digest.actionNeeded.total
    },
    events: {
      byUrgency: withoutFixtures(digest.events.byUrgency),
      byImportance: withoutFixtures(digest.events.byImportance),
      allEmails: withoutFixtures(digest.events.allEmails ?? []),
      total: digest.events.total
    },
    jobOffers: {
      byUrgency: withoutFixtures(digest.jobOffers.byUrgency),
      byImportance: withoutFixtures(digest.jobOffers.byImportance),
      allEmails: withoutFixtures(digest.jobOffers.allEmails ?? []),
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

/** A mail that changed what past applications teach (rejection, interview, new application). */
export interface LearningChange {
  at: string;
  emailId: number | null;
  kind: string;
  summary: string | null;
  change: string;
}

export interface ApplicationLearning {
  applications: number;
  /** What the model that rates job postings is told; empty when no group stands out. */
  notes: string;
  changes: LearningChange[];
}
