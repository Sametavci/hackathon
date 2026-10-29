// Table-game content ported from the lunch2-live prototype. Every entry carries both languages:
// the active UI language is shown first, the other one as a small subtitle.
export interface Bilingual { en: string; de: string }
const b = (en: string, de: string): Bilingual => ({ en, de });

export const IMPOSTOR_CATEGORIES = ['Food', 'Campus', 'Places', 'Hobbies'] as const;
export type ImpostorCategory = typeof IMPOSTOR_CATEGORIES[number];
export type ImpostorChoice = ImpostorCategory | 'Mixed';

export const IMPOSTOR_CATEGORY_NAMES: Record<ImpostorChoice, Bilingual> = {
  Mixed: b('Mixed', 'Gemischt'), Food: b('Food', 'Essen'), Campus: b('Campus', 'Campus'),
  Places: b('Places', 'Orte'), Hobbies: b('Hobbies', 'Hobbys'),
};

export const IMPOSTOR_WORDS: Record<ImpostorCategory, Bilingual[]> = {
  Food: [b('Pizza', 'Pizza'), b('Sushi', 'Sushi'), b('Pancakes', 'Pfannkuchen'), b('Pretzel', 'Brezel'), b('Ice cream', 'Eis'),
    b('Curry', 'Curry'), b('Burger', 'Burger'), b('Spaghetti', 'Spaghetti'), b('Döner', 'Döner'), b('Chocolate', 'Schokolade')],
  Campus: [b('Library', 'Bibliothek'), b('Exam', 'Prüfung'), b('Lecture', 'Vorlesung'), b('Coffee machine', 'Kaffeeautomat'),
    b('Group project', 'Gruppenarbeit'), b('Laptop', 'Laptop'), b('Deadline', 'Abgabetermin'), b('Student ID', 'Studierendenausweis')],
  Places: [b('Beach', 'Strand'), b('Train station', 'Bahnhof'), b('Airport', 'Flughafen'), b('Cinema', 'Kino'),
    b('Supermarket', 'Supermarkt'), b('Mountain', 'Berg'), b('Museum', 'Museum'), b('Swimming pool', 'Schwimmbad')],
  Hobbies: [b('Football', 'Fußball'), b('Guitar', 'Gitarre'), b('Hiking', 'Wandern'), b('Video games', 'Videospiele'),
    b('Baking', 'Backen'), b('Dancing', 'Tanzen'), b('Photography', 'Fotografie'), b('Yoga', 'Yoga')],
};

export const TRUTH_IDEAS: Bilingual[] = [
  b('a place you’ve been to', 'ein Ort, an dem du warst'), b('a food you can’t stand', 'ein Essen, das du nicht magst'),
  b('something you did as a child', 'etwas, das du als Kind gemacht hast'), b('a hidden talent', 'ein verstecktes Talent'),
  b('your first job', 'dein erster Job'), b('something you’ve won', 'etwas, das du gewonnen hast'),
  b('a strange habit', 'eine seltsame Angewohnheit'), b('an animal you’ve touched', 'ein Tier, das du schon angefasst hast'),
];

export const WOULD_YOU_RATHER: [Bilingual, Bilingual][] = [
  [b('Always be 10 minutes early', 'Immer 10 Minuten zu früh sein'), b('always be 10 minutes late', 'immer 10 Minuten zu spät sein')],
  [b('Live without music', 'Ohne Musik leben'), b('live without films and series', 'ohne Filme und Serien leben')],
  [b('Speak every language', 'Jede Sprache sprechen'), b('play every instrument', 'jedes Instrument spielen')],
  [b('Only eat sweet food', 'Nur noch Süßes essen'), b('only eat savoury food', 'nur noch Herzhaftes essen')],
  [b('Have all lectures at 7 am', 'Alle Vorlesungen um 7 Uhr morgens'), b('have all lectures at 8 pm', 'alle Vorlesungen um 20 Uhr abends')],
  [b('Be able to fly', 'Fliegen können'), b('be invisible', 'unsichtbar sein')],
  [b('Live in a big city', 'In einer Großstadt leben'), b('live in a small village', 'in einem kleinen Dorf leben')],
  [b('Give a talk to 500 people', 'Einen Vortrag vor 500 Leuten halten'), b('sing alone in front of 20 people', 'allein vor 20 Leuten singen')],
  [b('Have a pet dragon', 'Einen Drachen als Haustier haben'), b('have a robot that cooks for you', 'einen Roboter haben, der für dich kocht')],
  [b('Have summer all year', 'Das ganze Jahr Sommer haben'), b('have winter all year', 'das ganze Jahr Winter haben')],
  [b('Talk to animals', 'Mit Tieren sprechen können'), b('travel through time once', 'einmal durch die Zeit reisen')],
  [b('Always sing instead of talking', 'Immer singen statt sprechen'), b('always whisper', 'immer flüstern')],
];
