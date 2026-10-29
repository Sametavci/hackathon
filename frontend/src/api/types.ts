// Wire types from docs/API_CONTRACT.md. Nullable fields are present on the wire.
export type Category = 'DOMESTIC' | 'INTERNATIONAL';
export type Campus = 'WUERZBURG' | 'SCHWEINFURT';
export type MatchStatus = 'WAITING' | 'MATCHED';
export type PairStatus = 'ACTIVE' | 'INCOMPATIBLE' | 'UNLINKED';
export type ConnectChoice = 'YES' | 'NO';
export type ConnectionStatus = 'NONE' | 'WAITING_FOR_THEM' | 'MUTUAL';
export type CardSource = 'AI' | 'FALLBACK';
export type ReasonCode = 'WAITING_FOR_NEXT_ROUND' | 'NOT_ENOUGH_DOMESTIC' | 'NOT_ENOUGH_INTERNATIONAL' | 'NO_COMMON_LANGUAGE' | 'FRIEND_DIFFERENT_CAMPUS' | 'FRIEND_DIFFERENT_MENSA' | 'FRIEND_NO_COMMON_SLOT' | 'FRIEND_NO_COMMON_LANGUAGE' | 'INVITER_ALREADY_MATCHED';
export interface FieldError { field: string; message: string }
export interface ErrorResponse { error: { code: string; message: string; details?: FieldError[] } }
export interface Slot { id: string; date: string; start: string; end: string; label: string }
export interface MensaHours { dayOfWeek: number; opens: string; lunchStart: string; foodUntil: string; closes: string }
export interface Mensa {
  id: string; campus: Campus; name: string; address: string; defaultForCampus: boolean;
  hours: MensaHours[]; sourceUrl: string; verifiedOn: string; slotIds: string[];
}
export interface MetaResponse {
  mensas?: Mensa[];
  campuses: { id: Campus; label: string; location: string }[];
  languages: { code: string; label: string }[];
  interests: { id: string; label: string; emoji: string }[];
  studyFields: { id: string; label: string }[];
  slots: Slot[];
}
export interface RegistrationRequest {
  mensaId?: string | null;
  displayName: string; category: Category; campus: Campus; languages: string[];
  interests: string[]; slotIds: string[]; studyField: string | null;
  inviteCode: string | null; consent: boolean;
}
export interface Reason { code: ReasonCode; message: string }
export interface InviteResponse { code: string; url: string }
export interface InvitePreview {
  code: string; inviterDisplayName: string; campus: Campus; valid: boolean;
  mensaId?: string; slotIds?: string[]; inviterMatchStatus?: MatchStatus;
}
export interface MeResponse {
  mensaId?: string | null;
  id: string; displayName: string; category: Category; campus: Campus;
  languages: string[]; interests: string[]; slotIds: string[]; studyField: string | null;
  matchStatus: MatchStatus; waitingReason: Reason | null; groupId: string | null;
  friend: { displayName: string; pairStatus: PairStatus; issue: Reason | null } | null;
  pendingInvite: InviteResponse | null;
}
export interface RegistrationResponse { accessToken: string; restoreUrl: string; me: MeResponse }
export interface GroupResponse {
  id: string; campus: Campus; slot: Slot; startsAt: string; location: string;
  members: { id: string; displayName: string; isMe: boolean; isMyFriend: boolean; interests: string[] }[];
  sharedInterests: string[]; commonLanguages: string[]; whyThisGroup: string[];
}
export interface CardsResponse {
  groupId: string; language: string; source: CardSource;
  cards: { id: string; text: string; topic: string }[];
}
export interface ConnectionItem {
  participantId: string; displayName: string; myChoice: ConnectChoice | null;
  status: ConnectionStatus; myContact: string | null; theirContact: string | null;
}
export interface ConnectionRequest { choice: ConnectChoice }
export interface ContactRequest { contact: string }
export interface SeedResponse {
  participants: { id: string; displayName: string; campus: Campus; category: Category; restoreUrl: string }[];
}
export interface MatchRunResponse {
  groupsCreated: { groupId: string; campus: Campus; slotId: string; memberNames: string[]; score: number }[];
  waiting: { participantId: string; displayName: string; reasonCode: ReasonCode }[];
}
export interface StatsResponse {
  registrations: Record<Category, number>; byCampus: Record<Campus, number>;
  matched: number; waiting: number; matchRate: number; groups: number; friendPairs: number; mutualConnections: number;
}
