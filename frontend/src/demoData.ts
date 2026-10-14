export type LearningItem = {
  id: string
  title: string
  type: 'page' | 'quiz' | 'assignment' | 'code'
  summary: string
  body: string[]
  deadline?: string
  question?: { text: string; options: string[]; correct: number; explanation: string }
}

export type Course = {
  id: string
  code: string
  title: string
  category: string
  color: 'sage' | 'lavender' | 'peach'
  teacher: string
  teacherInitials: string
  modules: { title: string; items: LearningItem[] }[]
}

export const courses: Course[] = [
  {
    id: 'dat250', code: 'DAT250', title: 'Programvareteknologi', category: 'BYGG NOE SOM VARER', color: 'sage', teacher: 'Alex Larsen', teacherInitials: 'AL',
    modules: [
      { title: 'En god start', items: [
        { id: 'dat250-welcome', title: 'Velkommen til DAT250', type: 'page', summary: 'En introduksjon til emnet og semesterprosjektet.', body: ['I dette emnet lærer du å bygge applikasjoner som henger sammen: fra brukergrensesnitt og API til database og utrulling.', 'Gjennom semesteret bygger vi et læringssystem. Målet er å forstå både teknologien og hvorfor vi velger å bruke den. Dette er eksempelinnhold til frontend-demoen.'] },
        { id: 'dat250-architecture', title: 'Hvordan henger delene sammen?', type: 'page', summary: 'Frontend, backend og database.', body: ['Frontend er delen brukeren møter. Den sender forespørsler til backend, som håndterer forretningslogikk og kommunikasjon med databasen.', 'Tenk på et emne: læreren oppretter det i grensesnittet, API-et validerer innholdet, og databasen tar vare på det. Hver del har et tydelig ansvar.'] },
      ] },
      { title: 'Data som varer', items: [
        { id: 'dat250-orm', title: 'Fra Java-objekter til databaser', type: 'page', summary: 'Bli kjent med relasjonsdatabaser og ORM.', body: ['En relasjonsdatabase organiserer informasjon i tabeller. Et emne kan for eksempel ha en id, en tittel, en beskrivelse og et semester.', 'ORM kobler objekter i programmet til rader i databasen. Med JPA kan vi beskrive en Course-entitet i Java og bruke et repository til å lagre og hente emner.', 'I semesterprosjektet er kurs et fint sted å begynne. Tenk gjennom hvilke felter som er påkrevd, og hvordan et kurs senere skal knyttes til brukere og moduler.'] },
        { id: 'dat250-quiz', title: 'En liten databasesjekk', type: 'quiz', summary: 'Ett spørsmål. Rask tilbakemelding.', body: ['Test forståelsen din før du går videre til oppgaven. Du kan prøve så mange ganger du vil.'], question: { text: 'Hva er hovedoppgaven til en primærnøkkel i en databasetabell?', options: ['Å sortere radene alfabetisk', 'Å identifisere hver rad entydig', 'Å kryptere dataene i tabellen'], correct: 1, explanation: 'En primærnøkkel identifiserer hver rad entydig, slik at vi kan finne og referere til riktig rad.' } },
        { id: 'dat250-assignment', title: 'Modeller ditt første emne', type: 'assignment', deadline: '2026-10-16', summary: 'Lag en enkel Course-entitet og forklar feltene du velger.', body: ['Lag et forslag til en Course-entitet med id, tittel, beskrivelse og semester. Vis hvordan den kan lagres i en relasjonsdatabase.', 'Legg ved et lite klassediagram og en kort begrunnelse for valgene dine. Oppgaven kan løses i prosjektgruppen.'] },
      ] },
      { title: 'API og kommunikasjon', items: [
        { id: 'dat250-api', title: 'Et API som er lett å bruke', type: 'page', summary: 'Ressurser, HTTP og tydelige endepunkter.', body: ['Et API gjør funksjonalitet tilgjengelig for andre klienter. For et læringssystem kan GET /api/courses hente en liste med emner.', 'Bruk tydelige ressursnavn og meningsfulle statuskoder. Når noe går galt, bør klienten få en forståelig feilmelding.'] },
        { id: 'dat250-code', title: 'Skisser et kursendepunkt', type: 'code', summary: 'Se et lite eksempel på en Spring-controller.', body: ['Dette kodeeksemplet viser formen på et enkelt endepunkt. I en full løsning vil en tjeneste og et repository hente emnene fra databasen.', 'Kodefeltet er et leseeksempel i denne demoen. Automatisk kjøring og vurdering kan bygges inn senere.'] },
      ] },
    ],
  },
  {
    id: 'dat251', code: 'DAT251', title: 'Moderne systemutvikling', category: 'GODE IDEER, SAMMEN', color: 'lavender', teacher: 'Nora Berg', teacherInitials: 'NB',
    modules: [
      { title: 'Samarbeid og prosess', items: [
        { id: 'dat251-welcome', title: 'Å bygge som et team', type: 'page', summary: 'Felles retning og små leveranser.', body: ['Et godt team gjør det enkelt å dele arbeid og forstå hverandres valg. Start med et felles mål, og bryt arbeidet ned i små, konkrete oppgaver.', 'Dette er eksempelinnhold. Emnet viser hvordan flere kurs kan organiseres i samme læringsrom.'] },
        { id: 'dat251-process', title: 'Fra idé til brukerhistorie', type: 'page', summary: 'Beskriv hva brukeren trenger og hvorfor.', body: ['En brukerhistorie beskriver et behov fra brukerens perspektiv. For eksempel: Som student vil jeg se kommende frister, slik at jeg kan planlegge uken.', 'Legg til enkle akseptansekriterier som gjør det mulig å vurdere om behovet er oppfylt.'] },
      ] },
      { title: 'Planlegg neste steg', items: [
        { id: 'dat251-quiz', title: 'Sjekk brukerhistorien', type: 'quiz', summary: 'Hva gjør en brukerhistorie nyttig?', body: ['Prøv et lite spørsmål om brukerhistorier.'], question: { text: 'Hva bør en brukerhistorie først og fremst beskrive?', options: ['Et brukerbehov og verdien det gir', 'Alle metodene i backend', 'Hvilken farge knappen skal ha'], correct: 0, explanation: 'Brukerhistorien beskriver hvem som trenger noe, hva de trenger og hvorfor det gir verdi.' } },
        { id: 'dat251-assignment', title: 'Planlegg neste sprint', type: 'assignment', deadline: '2026-10-19', summary: 'Velg tre brukerhistorier og lag en gjennomførbar plan.', body: ['Velg tre brukerhistorier for semesterprosjektet. Prioriter dem, og bryt hver historie ned i mindre oppgaver.', 'Beskriv hva som skal være klart når sprinten er ferdig, og hvordan dere vil vise det frem.'] },
      ] },
    ],
  },
  {
    id: 'dat102', code: 'DAT102', title: 'Algoritmer og datastrukturer', category: 'FINN MØNSTERET', color: 'peach', teacher: 'Emil Solheim', teacherInitials: 'ES',
    modules: [
      { title: 'Grunnleggende byggesteiner', items: [
        { id: 'dat102-welcome', title: 'Velg riktig datastruktur', type: 'page', summary: 'Ulike strukturer for ulike behov.', body: ['En god datastruktur gjør det enklere å lagre og behandle informasjon. Valget avhenger av hvilke operasjoner programmet trenger å gjøre ofte.', 'Dette er et eksempel på et tredje emne i demoen, med egne moduler og egen fremdrift.'] },
        { id: 'dat102-lists', title: 'Lister og tabeller', type: 'page', summary: 'Lagring i en bestemt rekkefølge.', body: ['Lister samler elementer i en rekkefølge. Noen implementasjoner gjør det raskt å slå opp et element, mens andre gjør innsetting enklere.', 'Tenk gjennom forskjellen mellom å finne et element med en indeks og å lete etter en bestemt verdi.'] },
        { id: 'dat102-trees', title: 'Trær og hierarkier', type: 'page', summary: 'Når data henger sammen i flere nivåer.', body: ['Et tre organiserer noder i et hierarki. Hver node kan ha barn, og vi starter vanligvis fra en rot.', 'Et kurss innhold kan også tenkes som et hierarki: kurs, moduler og innholdselementer.'] },
      ] },
      { title: 'Tenk effektivt', items: [
        { id: 'dat102-complexity', title: 'Hvor raskt vokser arbeidet?', type: 'page', summary: 'En introduksjon til tidskompleksitet.', body: ['Tidskompleksitet beskriver hvordan mengden arbeid vokser når inputen blir større.', 'Et lineært søk må i verste fall undersøke alle elementene. Et binært søk kan halvere søkeområdet for hvert steg, men krever sorterte data.'] },
        { id: 'dat102-quiz', title: 'Hva krever binærsøk?', type: 'quiz', summary: 'Test hva du husker om søking.', body: ['Velg forutsetningen som må være på plass.'], question: { text: 'Hva kreves for å bruke vanlig binærsøk i en tabell?', options: ['Alle verdiene må være unike', 'Tabellen må inneholde tekst', 'Elementene må være sortert'], correct: 2, explanation: 'Binærsøk bruker sorteringen til å avgjøre hvilken halvdel av søkeområdet som kan fjernes.' } },
        { id: 'dat102-assignment', title: 'Utforsk søkealgoritmer', type: 'assignment', deadline: '2026-10-23', summary: 'Sammenlign lineært søk og binærsøk.', body: ['Implementer lineært søk og binærsøk for en sortert tabell. Tell hvor mange sammenligninger hver algoritme bruker.', 'Sammenlign resultatene for små og store tabeller. Forklar kort hva du observerer.'] },
      ] },
    ],
  },
]

export const initialCompleted = ['dat250-welcome', 'dat250-architecture', 'dat251-welcome', 'dat102-welcome', 'dat102-lists', 'dat102-trees', 'dat102-complexity']

export const formatDate = (date: string) => new Intl.DateTimeFormat('nb-NO', { day: 'numeric', month: 'short', timeZone: 'Europe/Oslo' }).format(new Date(`${date}T12:00:00Z`))
