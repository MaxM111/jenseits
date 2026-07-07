# Jenseits

## Wiederholung

### Joins

- Inner Join
  - "Normal" Join
- Left Outer Join
  - Returns all tuples from left relation, with null if there was no match from right relation
- Right Outer Join
  - Returns all tuples from right relation, with null if there was no match from left relation
- Full Outer Join (union of left and right outer join)
  - Union of Left and Right Outer Join

## E-Commerce

- Schema-Evolution
  - Anpassung der DB über Zeit während sie operational ist (typisch in production)
- Sparsity
  - Je höher die sparsity, desto höher die Anzahl an NULL-Werte

### Physische Speicherung

- Begriffe:
  - Higher-Order View: Name der Spalte ist Attributwert

#### Horizontal

- Klassisch
- Tupel werden hintereinander gespeichert in einer Page
  - Page header wächst nach unten
  - Tupel wachsen nach oben
- Probleme:
  - Selbst bei hoher sparsity, werden vollständige Tupel gespeichert
  - Inflexibel

#### Vertikal

- Pro non-null, non-primary-key Wert, gibt es eine Zeile in der vertikalen Tabelle
  - Tupel: (oid (primary key), key (Attributname), val (Wert))
  - $\Rightarrow$ Keine Zeile für Null-Werte
  - Limitation: val muss einen Typ (z.b. varchar) wählen
    - Kleiner Fix: Man kann nach Typen die vertikale Tabelle partitionieren (aber horizontal trotzdem alles strings)

- Spezialfall: Wenn ein Tupel nur Null-Werte hat, dann fehlt er in der vertikalen Tabelle und kann nicht mehr konstruiert werden
  - Lösung: Joine vertikale Tabelle mit oid-tabell, damit fehlende oids mit Null-Werten in der vertikalen Tabelle eingetragen werden

#### Binär

- Für jedes Attribut $a_i$, erstelle eine Tabelle (oid, $a_i$), wo für jeden primary key, der Wert für $a_i$ gespeichert wird
  - Schema-Evolution: Um ein weiteres Attribut hinzuzufügen, muss man nur eine neue Tabelle anlegen
  - man könnte entweder NULL nicht speichern oder schon mitspeichern:
    - NULL wird inkludiert:
      - Sparsity wird nicht beachtet
      - View is ein Natural Join über alle Tabellen
    - NULL wird weggelassen:
      - Bei hoher sparsity werden NULL nicht mitgespeichert
      - View is ein outer left join über alle Tabellen, wobei rechts die $i$-te Tabelle ist, die hinzukommt
        - hier gilt wieder: wenn eine Zeile nur NULL hat, dann geht sie verloren, also join mit oid-tabelle

### Lösung

- Das logische Schema ("frontend") ist horizontal
- Das physische/interne Schema ("backend") ist je nachdem
- Somit braucht man Sichten, damit das logische Schema horizontal bleibt

#### Operatoren für vertikale Modellierung

- definiert auf die ersten $k$ Attribute
- $v2h^k$
  - Man fängt an mit allen distinct oids in der vertikalen Tabelle (Projektion is distinct)
  - Für jedes Attribut, left joined man die vertikale Tabelle, die nach dem Attribut gefiltert ist
  - Geht nicht ganz in relationaler Algebra, aber man kann SQL dafür generieren
- $h2v^k$ (Erstellung der vertikalen Relationen)
  - Für jedes Attribut, projiziert man oid, "$a_i$" (key) und $a_i$ (value) von allen Tupeln, wo $a_i \not = null$
  - Diese Projektionen vereinigt man dann um die vertikale Tabelle zu bekommen
    - Damit eine Zeile mit nur NULL mitinkludiert wird, werden dann noch alle Tupel, die nur NULL-Werte haben (also NULL für jedes Attribut) mitvereinigt

- Somit kann man nun jede Anfrage "übersetzen" mithilfe dieser Operatoren

##### Rewritings

- Anforderungen
  - Input: Beliebiger Algebraausdruck auf horizontale Repräsentation
  - Output: Horizontale Repräsentation vom Ergebnis

###### Projektion

$$\pi_{oid, a_1,\dots,a_k}(H)$$
$$= \pi_{oid}(V) ⟕  \pi_{oid, val}(\sigma_{key='a_0'}(V)) ⟕  \dots) $$
Also eigentlich was v2h macht (glaube ich).

###### Selektion

- Eine Bedingung:
  $$\pi_{oid}(\sigma_{a_1 \theta \text{'a1'}}(H))$$
  $$= \pi_{oid}(\sigma_{key='a_1' \land val \theta 'a1'}(V))$$
- Zwei (illustrativ für $n > 1$) Bedingungen:
  $$\pi_{oid}(\sigma_{a_1 \theta \text{'a1'} \land a_2 \theta 'a_2'}(H))$$
  $$= \pi_{oid}(\sigma_{key='a_1' \land val \theta 'a1'(V)}) \cap \pi_{oid}(\sigma_{key='a_2' \land val \theta 'a2'(V)})$$

Also einfach nach den Rows mit dem gesuchten Attribut **und** Attributwert filtern. Bei mehreren Bedingungen mit $\land$ verknüpft, selektiere für die Bedingungen einzeln und nehme Mengenschnitt.

## Logik-basierte Anfragesprachen

- Logik als Anfragesprache (also mittels Mathematik)

### Tupelkalkül

Allgemeine Form:
$$\{t.oid \mid \varphi(t)\}$$
Also die Menge an Tupel $t$, die die Formel $\varphi$ erfüllen. Mit Punkt-notation (alternativ auch indexieren) greift man auf die Attribute zu.

Bsp.:
$$\{t \mid KUNDE(t) \land \exists s (AUFTRAG(s) \land s.kdnr = t.kdnr)\}$$

### Bereichskalkül

Allgemeine Form:
$$\{x_1, \dots, x_n \mid \varphi(x_1, \dots, x_n)\}$$
Es müssen nicht alle Variablen belegt werden, man kann stattessen "\_" schreiben.

Bsp.:

Alle Orte, in denen es Kunden gibt (z.b. bei Schema KUNDE(oid, vorname, nachname, ort)):
$$\{x \mid KUNDE(\_, \_, \_, x)\}$$

Alle Kunden aus Salzburg:
$$\{x, y, z \mid KUNDE(x, y, z, "Salzburg")\}$$

Waren ohne Bestellung (Join auf $x$):
$$\{x, y \mid WARE(x, y, \_) \land \neg AUFTRAG(\_,\_,x,\_)\}$$

### Sichere Anfragen

Eine Anfrage ist sicher, wenn für jeden Datenbankzustand, ein endliches Ergebnis geliefert wird.
Zum Beispiel ist folgende Anfrage nicht sicher:
$$\{k \mid \neg KUNDE(k)\}$$
Domäne ist unendlich groß, $KUNDE(k)$ ist nicht unendlich groß, somit ist $\neg KUNDE(k)$ unendlich groß und somit liefert diese Anfrage ein unendlich großes Ergebnis.

### Datalog

- basiert auf Kalkülen
- Eigenschaften wie formale Nachweisbarkeit
- Teilmenge von Prolog
- Man kann transitive Hülle berechnen (geht nicht in Relationale Algebra (nur für Maximallänge $k$))

#### Basics

- Anhand Bsp:
- Relation `Person(X, Y, Z)` (Attribute: Name, Alter, Geschlecht)
- Relation `Elternschaft(X, Y)` (Attribute: Elternteil, Kind)
- Mögliches Tupel (logischer Fakt): `Person(Klemens, 32, m)`, `Elternschaft(John, Jeff)`
- Anfrage: `Elternschaft(John, x)` ("Kinder von John")

- **Extensionale Datenbank**
  - Basis-Daten der Datenbank (Person, Elternschaft)
- **Intensionale Datenbank**
  - Abgeleitete Relationen, "Views"
    - z.B.: `Vater(X, Y) :- Person(X, \_, m), Elternschaft(X, Y)`

##### Definition von Datalog Programm

- Atom: $P(X_1, \dots, X_n)$
- Literal: `[ not ] Atom`
- Klausel: Disjunktion von Literalen
- **Horn-Klauseln**: Klausel mit maximal ein positiven Literal
  - $\neg p_1 \cup \neg p_2 \cup \neg p_3 \cup u$
- Datalog: Menge von Horn-Klauseln
- **Keine** komplexen Terme (z.b. `P(f(1), 2)`)

##### Probleme bei Logischer Anfragesprache

- Erfüllbarkeit (SAT) von Aussagenlogik ist NP-vollständig
- FOL ist nicht entscheidbar
- $\Rightarrow$ Beschränkung auf **Horn-Klauseln**

##### Rekursion in Datalog

- **Lineare Rekursion**
  - **Links-rekursiv**

        FollowOn(x, y) :- SequelOf(x, y)
        FollowOn(x, y) :- FollowOn(x, z), SequelOf(z, y)

  - **Rechts-rekursiv**

        FollowOn(x, y) :- SequelOf(x, y)
        FollowOn(x, y) :- SequelOf(x, z), FollowOn(z, y)

- **Nicht-lineare Rekursion**

        FollowOn(x, y) :- SequelOf(x, y)
        FollowOn(x, y) :- FollowOn(x, z), FollowOn(z, y)

  - Manche System können keine nicht-lineare Rekursion
  - TODO: still not sure what these mean in practice

```
Vorfahre(X, Y) :- Elternschaft(X, Y)
Vorfahre(X, Y) :- Elternschaft(X, Z), Vorfahre(Z, Y)
```

- **Beweistheoretischer** Ansatz für transitive Hülle:
  1. Beginne mit leerer Menge
  2. Wende alle Regeln an
  3. Wenn Fixpunkt (also keine neuen Tupel), dann fertig
  4. Sonst wiederhole von 2.
- **Modelltheoretischer** Ansatz für transitive Hülle:
  1. Bilde _alle_ Folgen von Tupel-Paaren (in der Theorie, natürlich eig. unendlich)
  2. z.b.: `((R3, R1), (R1, R2)), ((R4, R2), (R1, R3)), ((R1, R2), (R2, R3))`
  3. Für jede Folge: Macht sie Modell wahr?

     Also für `R1(...) :- R2(...), ..., RN(...)` mit Variablen $x_1, \dots, x_m$:

     $$= \forall x_1, \dots, x_m : R_2(\dots) \land \dots \land R_n(\dots) \Rightarrow R_1(\dots)$$
     $$= \forall x_1, \dots, x_m : \neg R_2(\dots) \lor \dots \lor \neg R_n(\dots) \lor R_1(\dots)$$
     Was eine Horn-Klausel ist.

     Eine Instanz erfüllt die Regel, wenn alle Atome wahr sind. D.h. wenn $R_2(\dots), \dots, R_n(\dots)$ wahr sind, dann ist $R_1(\dots)$ wahr.

- Beweistheoretisch und Modelltheoretisch sind equivalent im Ergebnis **wenn es sich um Horn-Klauseln handelt**.
- Modelltheoretische Semantik beschreibt Semantik als allgemeine FOL
- Beweistheoretische Semantik beschreibt Semantik mittels einem Algorithmus

#### Monotonie Eigenschaft

- monoton $\coloneqq$ Vergrößerung des Inputs macht nicht Output kleiner
- Datalog ist monoton, weil keine Negation in Definitionen erlaubt (dann keine Horn-Klausel mehr)
- Differenz ($-$) ist **nicht monoton**:
  1. $S = \{1, 2\}, R = \{1\}$
  2. $S - R = \{2\}$
  3. Sei $R' = \{1,2\}$
  4. Dann ist $S - R' = \{\}$, also ist **kleiner geworden** obwohl $R'$ größer ist

#### Ausdrucksmächtigkeit

- RA $\coloneqq$ Relationale Algebra
- Datalog $\cap$ RA $=$ Datalog ohne Rekursion $=$ RA ohne Differenz

#### Negation in Datalog

```prolog
P(X) :- R(X), ¬Q(X).
Q(X) :- R(X), ¬P(X).
```

Nehme an die extensionale Datenbank hat nur ein Tupel in R (0).

| P   | Q   | Begründung                 |
| --- | --- | -------------------------- |
| ∅   | ∅   | Startzustand               |
| {0} | ∅   | `¬Q(0)` ⇒ `P(0)`           |
| ∅   | {0} | `¬P(0)` ⇒ `Q(0)`           |
| {0} | ∅   | `¬Q(0)` ⇒ `P(0)`           |
| ∅   | {0} | `¬P(0)` ⇒ `Q(0)`           |
| ... | ... | Oszillation, kein Fixpunkt |

- Beweistheoretische Semantik: Algorithmus terminiert nicht
- Modelltheoretische Semantik: Es kann mehrere Modelle geben
- $\Rightarrow$ nicht mehr equivalent
- $\Rightarrow \neg P$ nur erlaubt, wenn $P$ vollständig berechnet wurde

##### Stratifikation

- Zeichne einen Abhängigkeitsgraphen der Regeln
  - Kante von $P$ nach $Q$ $\iff$ Regel von $P$ beinhaltet $Q$
  - Kante is mit $\neg$ gelabelled, wenn es in der Regel negiert vorkommt
- $\Rightarrow$ Wenn es Zyklus mit mindestens einer Negation gibt, dann ist es störend (nicht mehr eindeutig).
- **Stratifikation**:
  - Partitioniere Programm in $n$ Ebenen, nummeriert mit $0, \dots, n - 1$, sodass:
    1. $R$ wird positiv in Regel für $S$ verwendet $\Rightarrow$ $R$ ist in der Ebene $k$, wobei $k \le layer(S)$
    1. $R$ wird negativ in Regel für $S$ verwendet $\Rightarrow$ $R$ ist in der Ebene $k$, wobei $k \lt layer(S)$

### Rekursion in SQL

```sql
WITH RECURSIVE Reaches(from_, to_) AS (
    SELECT from_, to_
    FROM Flights
    UNION
    SELECT R1.from_, R2.to_
    FROM Reaches AS R1, Reaches R2
    WHERE R1.to_ = R2.from_
) SELECT * FROM Reaches;
```

- Rekursion ist nicht linear (gewisse System können Linearität fordern)
- Mutual Recursion nur erlaubt, wenn monoton (selbes Problem wie bei Datalog)
  - Mutual recursive Relation $S$ in $R$ ist monoton $\iff$ Tupel zu $S$ hinzufügen, fügt Tupel zu R hinzu oder R verändert sich nicht
  - Sonst kann oszillieren

- **Aggregation kann Monotonizität verletzen**

  ```sql
  WITH RECURSIVE P(x) AS (
    (SELECT * FROM R)
    UNION
    (SELECT * FROM Q)
  ),
  RECURSIVE Q(x) AS (
    SELECT SUM(x) FROM P
  )
  SELECT * FROM P;
  ```

  | Runde | P                        | Q         |
  | ----: | ------------------------ | --------- |
  |     0 | ∅                        | ∅         |
  |     1 | {(12), (34)}             | ∅         |
  |     2 | {(12), (34)}             | {(46)}    |
  |     3 | {(12), (34), (46)}       | {(46)}    |
  |     4 | {(12), (34), (46)}       | {(92)}    |
  |     5 | {(12), (34), (46), (92)} | {(92)}    |
  |     6 | {(12), (34), (46), (92)} | {(138)}\* |

## In-Memory Systeme

- Traditionell viel mit Hard-Drive gearbeitet
  - Viele Architekture optimieren durch Minimierung von Disk-Zugriff
  - Daten sind auf Disk, Puffer im Hauptspeicher
- Heute aber viel mehr Hauptspeicher, also kann stattdessen genutzt werden
  - Disk eher nur für Redundanz, während eigentliche Daten in Hauptspeicher bleiben
  - Puffermanagement muss nicht mehr DBMS machen - OS kann übernehmen

- Neue Zugriffslücke ist nicht Disk Access, sondern Von-Neumann Bottleneck

### Cache

- Prinzip der Lokalität
  - Hot data passt meist vollständig in Cache
  - 90% der Zeit nur 10% des Code ausgeführt
  - **Räumliche Lokalität**
    - z.B. Scan von Spalte
  - **Zeitliche Lokalität**
    - z.B. Selektionsprädikate
- **Cache Hit**
  - Daten im Cache $\rightarrow$ Kein Hauptspeicher-Zugriff
- **Cache Miss**
  - Daten nicht im Cache
    -\*\* $\rightarrow$ Hauptspeicher-Zugriff
    - Lesen einer **Cache-line** (16-128 Byte) und alte damit verdrängen
    - CPU **stalled** bis Daten verfügbar

### DBMS Performanzprobleme

- Schlechte Code-Lokalität wegen polymorphen Funktionen
- Volcano-Typ Iterator-Verarbeitungsmodell (Pipelining)
  - Tupel-at-a-time
  - Tupel werden einzeln durch Anfrageplan geschickt
  - Oft parallelisiert $\Rightarrow$ Mehr instruction-cache-misses und data-cache-misses (weil größerer Programmzustand)
- Schlechte Daten-Lokalität
  - Selten verwendete Daten sind kalt
  - Index-Navigation mit Bäumen ist nicht cache-friendly

### Speicherlayout

- Row-Store
  - Pages speichern Rows hintereinander
  - Gut: Write/Tupelrekonstruktion
  - Schlecht:
- Column-Store
  - Pages speichern Columns hintereinander
  - Gut: Lesen von einzelnen Attributen (z.b. Aggregation)
  - Bessere Datentyp-spezifische Kompression

- Bsp.: Selektion von einem Attribut und Aggregation (`COUNT`) von ungeordneter Tabelle
  - Ausführung: Full Table Scan
  - Row-Store: Viele data-cache-misses, weil Rest des Tupels gecached wird,
    aber nicht das Attribut vom nächsten Row
  - Column-Store: Data-cache-friendly, weil alle zu
    überprüfende Werte nebeneinander sind

- Bsp.: Tupelrekonstruktion (`SELECT * ...`)
  - Column-Store würde pro Attribut joinen müssen

- Bsp.: `INSERT`/`UPDATE`
  - Column-Store: Auf mehrere Seiten verteilt (also viele TLB-misses)

- Man muss also nach **Workload** entscheiden:

### Verarbeitungsmodell

- Definiert:
  - wie Operatorbaum abgearbeitet wird
  - wie Zwischenergebnisse weitgegeben werden

#### Tuple-At-A-Time Volcano Model

- Tupel werden durch Operatoren gepipelined
- Ziel ist Minimierung von Zwischenergebnis (um in RAM zu passen)
- Einfach zu parallelisieren
- Operator bekommt mit Aufruf von `next()` nächstes Tupel (Iterator-style)
- Probleme:
  - Function-Call-Overhead weil Operatoren sich gegenseitig aufrufen
  - Instruktion/Data-cache-misses bei Parallelisierung

- Profiling zeigt nur 10% der Zeit wird für Berechnung genutzt:
  - CPU wartet viel
  - Einzelner Zugriff macht Compiler-Optimierungen schwierig

#### Operator-At-A-Time Bulk Processing

- Ausführung von gesamten Input auf Operator
- Operator ist auf **Spalten** definiert (nicht Tupel oder Relation)
  - Selektion: Spalte $\mapsto$ `[TID]` (alle TIDs, die "überleben")
  - Join: Spalte $\times$ Spalte $\mapsto$ `[(TID, TID)]` (alle TIDs, die einen Joinpartner finden)
  - Materialisierung: `[TID]` $\mapsto$ Spalte (Erhalte Spaltenwert für TID)
- Operator pro Spalte nur einmal aufgerufen und looped über ganze Spalte
- Zwischenergebnis vollständig im RAM

- Code passt in Instruction-cache
- Compiler kann leichter optimieren (loop-unrolling (doesn't sound good imo), vektorisierung)
- Vorteile:
  - Code/Data-Cache-friendly
  - leicht optimierbarer Code
- Nachteile:
  - Daten passen nicht vollständig in Cache $\rightarrow$ Memory-access
  - Man braucht genug RAM für Zwischenergebnisse

#### Vektorisierte Ausführung

- Arbeitet auf Spalten
- Volcano-Iteration: `next()` liefert kein Tupel, sondern Vektor
- Vektor muss klein genug sein für cache, aber groß genug für wenig function-call overhead

#### Data-Centric Code Generation

- Generiere Implementierung von Operatoren zu Laufzeit
- Anfrageplan traversiert mit DFS:
  - First visit: Spezfischer Code wird generiert und kompiliert (`produce()`)
  - Last-visit: Ausführung vom generiertem Code (`consume`)
- Entweder:
  - Ein loop mit Prädikaten ($\rightarrow$ keine Zwischenergebnisse)
  - Mehrere loops pro Prädikat, die als Zwischenergebnis Indexe weitergeben (cache-friendly, weil pro Attribut abgearbeitet)

### Workloads

#### Transaktionaler Workload (OLTP)

- Typisch: Wenige Tupel werden vollständig geholt
- Mischung von Read/Write
- Optimierung: Throughput
- Wenn wenig Write, kann Column-Store sinnvoll sein

#### Analytischer Workload (OLAP)

- Typisch: Für wenige Attribute holt man viele Tupel
- Große Datenmenge, aber generell nur gelesen
- Column-Store macht hier Sinn
- Verarbeitungsmodell meist Vector-at-a-time oder Code-Compilation
- Transaktionen?
  - (naiv) werden weggelassen, sperre einfach Datenbank bei Updates
  - **Delta Store**

##### Delta Store

- **Main-Store**
  - Column-Store
  - read-only (optimiert dafür)
  - für Write wird gesperrt
- **Delta-Store**
  - Row-Store
  - Sammelt `UPDATE`, `DELETE`, `INSERT`
  - Wenn voll, Synchronisation mit Main-Store mittels exclusive lock

- Read ist dann Ergebnis von Main-Store und Delta-Store gemerged

### Kompression

- Ziel: Cache-friendlier durch Size-Reduktion
- Anforderungen für Verfahren:
  - Lossless
  - Lightweight
  - Ideal: Anfrage auf komprimierte Daten möglich
- Folgende Beispiele eher für OLAP gedacht

#### Wörterbuchkodierung

- Sinnvoll für Strings
- Mappe Strings zu unique Bits mittels Wörterbuch
- Komprimierung: $O(log(n))$, Dekomprimierung: $O(1)$
- Anfrage darauf teils möglich (Equality, possibly Order...)

#### Bit Packing

- Slacks $\coloneqq$ Ungenutzte Bits
- Wenn z.b. für Wörterbuchkodierung nur 4 Bits gebraucht werden (weil $n \le 16$),
  dann wird es trotzdem als `int16` gespeichert
- Stattdessen, speichere 4 Codes in einem `int16`
- Nachteil: Verarbeitung darauf nicht mehr trivial (Bit-Operationen notwendig)

#### Lauflängenkodierung

- Speichere Duplikate nur einmal und merke in separater Tabelle wie oft es vorkommt
  - z.b. Wenn "Bayern" 3-mal vorkommt, speichere nur einmal mit Anzahl an Vorkommen (3)
- Kombinierbar mit Wörterbuchkodierung

### HyPer

- Transaktionen sind sequentiell (RAM ist eh schnell)
- **Copy on Write**
  - Wenn Datenobjekt durch OLTP Anfrage geändert wird, wird neue Page mit alten Daten erstellt
  - Korrekter Zugriff über Zeitstempel
  - OLTP kann dann Page modifizieren

## Datenmodell

- Beschreibung der zulässigen Zustände (Schema)
- Beschreibung der zulässigen Zustandsübergänge (Operatoren)

- Bsp.: Relationenmodell
  - Endliche Menge von Attributen $U$
  - Endliche Domäne $D_{\tau}$
  - Funktion $dom: U \mapsto D$
  - Tupel, Relation, $\dots$
  - Übergänge formalisiert in Algebra

### Typen

- Typ is Menge von Objekten gleicher mathematischer Struktur (Schema einer Relation, OOP Klasse)
- Instanz ist Element der Domäne (Relation, OOP Objekt)
- Domäne eines zusammengesetzten Typs ist das Produkt der Domänen seiner Attribute

#### **Polymorphes Typsystem**

- **Atomare Typen**
  - `int`, `bool`, $\dots$
- **Typkonstruktoren** (Neue Typen aus bestehenden erzeugen) (**kein Typ, nur Konstruktor**)
  - $list(t_1, \dots, t_n)$, $record(t_1, \dots, t_n)$
  - Relation is Menge von Tupel ($relation(t_1, \dots, t_n)$)
  - Tupel ist Record von Attributen ($tuple(a_1, \dots, a_m)$)
- **Polymorphe Konsistenzbedingungen**
  - Primärschlüssel/Fremdschlüssel

### Operatoren

- Mathematische Funktionen anwendbar auf Instanzen von bestimmten Typen
  - z.b: $+$ ist anwendbar auf Zahlen, $and$ ist anwendbar auf `bool`, $\dots$

## XML Dokument

- Dokument hat Struktur (Baum) und Daten (Text) zusammen
  - Relation trennt Struktur (Schema) von Daten (Pages mit Daten)

- **Wohlgeformt** $\coloneqq$ XML Syntax ist korrekt
- **Gültig** $\coloneqq$ Korrekt gemäß DTD (Vorraussetzung, dass es wohlgeformt ist)

- Kein festes Schema bei XML (beliebige tags und Struktur) $\rightarrow$ **semistrukturiert**
  - Daher gibt es DTD (weil XML semistrukturiert ist)

### XML als Datenmodell

- Frei wählbar, ob Attribut als XML-Element oder XML-Attribut behandelt wird

- Man könnte XML wie relationales Modell strukturieren:
  - Am Anfang Schema (`<attributes>` mit Attributnamen)
  - Dann alle Tupel (`<tupel>` mit `<attributwert>`)

### Document-type-definition (DTD)

- Kontextfreie Grammatik (Somit rekursive Regeln möglich)

- Atomare Typen: `#PCDATA`, `#CDATA`
- Typ-Konstruktoren:
  - `type*`: $n$-mal ($n \ge 0$)
  - `type+`: $n$-mal ($n > 1$)
  - `type?`: $n$-mal ($n \le 1$)
  - `(type, ..., type)`: "Record"
  - `(type | type)`: "Oder"
- Mit `ID` und `IDREF(S)` können OIDs und Referenzen beschrieben werden
- **Primärstruktur**
  - Alle Elemente und alle Attribute vom Typ `CDATA`
- **Sekundärstruktur**
  - Attribute vom Type `ID`, `IDREF(S)`
- Nachteil:
  - Keine Typisierung (von Referenzen)
  - keine Konsistenzbedingungen (z.b. `father` attribute kann auf Frauen zeigen)

## Deklarativer Zugriff auf semistrukturierte Daten

- Relationale Algebra ist abgeschlossen (Input: Relation, Output: Relation)
  - $\Rightarrow$ Bei XML muss Baum zurückgeben, da Input Baum ist (z.b. Wurzel "result" mit Kindern als Ergebnis)

### XPath

- (Syntax muss nicht gemerkt werden, nur zur Merkhilfe)
- Absoluter vs relativer Pfad spezifizierbar (`name` vs `.name`)
- Globale Suche im Dokument (`//name`)
- Auf Kinder eines Elements zugreifen (`name/*`)
- Auf Attribute eines Elements zugreifen (`name@*`)
- Selektion (`name[a1 or a2 = "Hans"]`, `book[.//firstname]`)
  - Somit auch Joins möglich
- Namespaces

#### Location Paths

- Besteht aus _Location Steps_
- **Location Step**:
  - **Achse**
  - **Node Test**
  - **Predicates**
- z.B.: `/descendant::figure[1]` - selektiere erstes Bild im Dokument
- z.B.: `//author/parent::book` - selektiere Autoren, dessen Parent ein `book` ist

## Relationale Speicherung von XML & XPath-Accelerator

### Edge Modell

- Speichere Nodes als Tabelle (Elemente von XML)
- Speichere Edges als Tabelle (Kanten von Baumstruktur)
- Ineffizient: Rekonstruktion benötigt viele Joins

### Räumliche Anfragen

- Anfragen wie "Welche Bars sind in der Nähe"
- Naiv: Berechne Distanz für jede Bar
- **R-Baum (Rectangle-Baum)**
  - Gruppiert (z.b.) 2-D Punkte nach ihrer Nähe zueinander
  - Blätter sind MBR (Minimum Bounded Rectangle, kleinstes Rechteck um Punkte einzuschließen)
  - Innerer Knoten (und auch Wurzel) hat als MBR die Vereinigung der MBR seiner
    Kinder
  - DBMS-Implementierung: GiST (Generalised Search Tree) (Generalised, weil nicht nur Rechtecke, z.b. auch Kreise)

### XPath Accelerator

- Ancestor, Descendant, preceding, following partitionieren Knoten (eines XML-Baums)
- Methodik:
  1. Annotiere alle Knoten mit **Preorder** und **Postorder** Nummer
  2. Verwende **Preorder als x-Koordinate**, **Postorder als y-Koordinate**
     und zeichne Funktions-Graph
  3. Für Kontextknoten $v$, partitioniere den Funktions-Graph in Quadranten,
     wo $v$ Origin ist.
  4. $\Rightarrow$ Für Knoten $u$ gilt dann:
     - $u$ ist Ancestor $\iff$ links oben
     - $u$ ist Descendantk $\iff$ rechts unten
     - $u$ ist Preceding $\iff$ links unten
     - $u$ ist Following $\iff$ rechts oben
- Schneller als Edge-Modell, da nur range-checks

#### Optimierung

##### Verkleinerung von Fenster

- Man muss nicht den ganzen Quadranten betrachten
  (von 0 bis Wert oder von Wert bis unendlich),
  sondern nur einen Teil davon
- Es gilt:
  $$pre(v_{child}) \le post(v) + height$$
  $$post(v_{leftest\_child}) \ge pre(v) - height$$
- Z.b. Descendants sind im rechten unteren Quadranten (post < post(v) && pre > pre(v))
  - $\Rightarrow pre \in (pre(v), \infty), post \in [0, post(v))$
  - $\Rightarrow pre \in (pre(v), post(v) + height), post \in [pre(v) - height, post(v))$
- TODO: überprüfe für andere Achsen

##### Zugriff mit nur einer Achse

- Nur für `descendants()`
- Annotiere diesmal anders:
  - Nur einen gemeinsamen counter
  - Annotiere preorder bei erstem Besuch
  - Annotiere postorder bei letztem Besuch
- Dann gilt: Descendant $\iff$ $pre > pre(v) \land pre < post(v)$

### Prüfungsfragen

- Wieso ist es vorteilhaft, die Struktur
  eines Dokumentbestands für die Speicherung/
  für die Evaluierung von Anfragen zu kennen?
  - Angelegte Relationen können Annahmen treffen
    - z.b. Wenn XML strenges Schema hat, muss man kein Edge-Modell verwenden - einfach normale Relationen
- Warum eignet sich XPath Accelerator für Realisierung von RDBMS Technologie?
  - Kein Edge Modell (langsam wegen vielen Joins)
  - Mittels Annotationen können Achsen schnell mittels range-checks gesucht werden
- Einen Location Step nach SQL wiedergeben können
- Eine Optimierung des XPath Accelerators erklären

## Zentralität in Graphen

- "Wie wichtig ist dieser Knoten?"
- Zentralität ist Entscheidungsgrundlage für
  - Werbetarget
  - Selbstverwaltung (Moderatorrechte vergeben)

- Arten:
  - Lokale Ansätze
    - InDegree
  - Eigenvektor-basiert
    - PageRank, Authority, Positional Weakness Function
  - Distanz-basierte
    - Proximity Prestige, Integrity

### Lokal-basiert (InDegree)

- einfach zu berechnen
- (un)gewichtet
- Nachteile:
  - "Ungewichtet"
    - Fake-likes
    - Wissenschaft: Ist es egal wer dich zitiert?

### Eigenvektor-basiert (PageRank)

- PageRank (PR) nur gerichtete Graphen
- Intuitiv:
  - Knoten ist wichtig, wenn andere Knoten ihn exklusiv referenzieren
  - $indegree(i) = 0 \implies PR_i$ is minimal
  - Knoten, die von wichtigen Knoten referenziert werden, haben höhere Wichigkeit (wenn exklusiv, dann sogar schneller)
  - Hoher $indegree(v)$ impliziert hohe Wichtigkeit
- Iterativ berechnet mit Dämpfungsfaktor $d$:
  1. Initialisiere PR mit $1/|V|$
  2. Pro Runde wird $PR_i$ geupdated (Dämpfungsfaktor $d$ (oft 0.85)):
     - $\frac{(1 - d)}{|V|}$ stellt sicher das PageRank nicht 0 wird
     - $+ d \cdot \sum_{\forall j \in \{(j, i)\}}\frac{PR_j}{outdegree(j)}$
       - Summe der PageRanks aller Knoten, die $i$ referenzieren
       - Je mehr $j$ andere Knoten referenziert, desto weniger Gewicht hat sein PageRank
       - "Gedämpft" mit $d$
  3. Ende nach Konvergenz
  - Effekt: PR wird gleichmäßig an referenzierte Knoten weitergegeben

### Distanz-basiert (Proximity Prestige)

- Influence Domain $I_i$: Knoten, die $i$ erreichen können (max: $|V| - 1$)
- $d(j, i) \coloneqq$ Distanz des kürzesten Pfades von $j$ nach $i$
  $$P_p(i) = \frac{\frac{|I_i|}{|V| - 1}}{\frac{\sum_{j \in I_i}d(j, i)}{|I_i|}}$$
  - $\frac{|I_i|}{|V| - 1}$: Normalisiere $I_i$
  - $\frac{\sum_{j \in I_i}d(j, i)}{|I_i|}$: Durchschnittliche Distanz

- Intuitiv:
  - $P_p(i) \in (0, 1]$
  - nur berechenbar wenn $|I_i| > 0$
  - Maximaler Wert:
    - $|I_i| = |V| - 1$ (Alle Knoten erreichen)
    - $\forall j \in V : d(j, i) = 1$ (Direkt erreichbar)
  - Minimaler Wert:
    - Kleine $|I_i| (kaum erreichbar)
    - Kürzeste Pfade sind sehr lang
  - Je näher (je kleiner $d(j, i)$) und je mehr Knoten ($I_i$) $i$ erreichen,
    desto wichtiger ist $i$

#### Betweeness Centrality

- Für alle Knotenpaare $j$ und $k$, wie viele von
  ihren Pfaden "durchqueren" Knoten $i$?
- Sei $P_{jk}$ die Anzahl kürzester Pfade zwischen $j$ und $k$
  $$C_B(i) = \sum_{j < k}\frac{P_{jk}(i)}{P_{jk}}$$
- ($j < k$, damit Knotenpaare nicht doppelt gezählt werden)
- Unterschied zu Proximity Prestige: Erreichbarkeit ist nicht wichtig,
  sondern wie sehr man den Knoten besuchen muss, um andere Knoten zu erreichen
  - z.b. vergleiche Stern ($i$ ist Mitte) und Clique

### Zentralitätsmaße

| Zentralitätsmaß     | Gewichtet (Mehrere Kanten, ± Gewichte) | Gewichtet (Einzelne Kanten, + Gewichte) | Ungewichtet (Einzelne Kanten) |
| ------------------- | :------------------------------------: | :-------------------------------------: | :---------------------------: |
| Lokal               |                   ✓                    |                    ✓                    |               ✓               |
| Eigenvektor-basiert |                   –                    |                    ✓                    |               ✓               |
| Distanz-basiert     |                   –                    |                    –                    |               ✓               |

- (Mehrere Kanten bedeutet mehrere Kanten zwischen zwei Knoten)
- Distanzbasierte Ansätze sind nicht auf gewichtete Graphen definiert
  (z.b. ist hohes Gewicht "näher" oder "entfernter"?)
- PageRank Ansätze nicht auf Graphen mit "mehreren Kanten" definiert
  (z.b. bei Weitergabe von PR, sollte jede Kante zwischen $i$ und $j$ beachtet werden?)
- Transformationen von Graphen möglich, aber Information geht verloren

## Mustererkennung mit Motifs

- Anforderung: Abgeschlossenheit (Graph in, Graph out)
- Schwierigkeit: Strukturen zu nehmen und zu erkennen sehr schwierig in "normalen" DBMS
  - Schlecht erkennbar was Bedürfnis ist
  - Viele Joins

### Graph Motif

- Formale Sprache für Graph Muster
  - Knoten/Kanten sind Terminale
  - Graphen sind Non-Terminale

#### Simple Graph Motif

```
graph Triangle {
  node v1, v2, v3;
  edge e1(v1, v2);
  edge e2(v2, v3);
  edge e3(v3, v1);
}
```

#### Composed Graph Motif

- Externes Interface: Elemente, die von außen sichtbar/referenzierbar sind

##### Concatenation

```
graph G2 {
  graph Triangle as X;
  graph Triangle as Y;
  edge e4(X.v1, Y.v1);
  edge e5(X.v3, Y.v2);
}
```

##### Unification

- Verschmelzen von Knoten (somit auch von dazugehörigen Kanten))

```
graph G2 {
  graph Triangle as X;
  graph Triangle as Y;
  unify X.v1, Y.v1;
  unify X.v3, Y.v2;
}
```

##### Disjunction

```
graph G4 {
  node v1, v2;
  edge e1(v1, v2);
  {
    node v3;
    edge e2(v1, v3);
    edge e3(v2, v3);
  } | {
    node v3, v4;
    edge e2(v1, v3);
    edge e3(v2, v4);
    edge e4(v3, v4);
  };
}
```

- Externes interface ist `node v1, v2` und `edge e1(v1, v2)`

##### Recursion

```
graph Path {
  {
    graph Path;
    node fn;
    edge e1(fn, Path.fn);
    export Path.ln as ln
  } | {
    node fn, ln;
    edge e1(fn, ln);
  };
}

graph Cycle {
  graph Path;
  edge e1(Path.fn, Path.ln);
}
```

- Externes interface ist `fn`, `ln` (ig not `e1`, because it's not used)

```
graph G5 {
  {
    graph G5;
    graph Triangle as T;
    export G5.v0 as v0;
    edge e1(v0, T.v1)
  } | {
    node v0;
  }
}
```

- Ein Stern, an die Dreiecke verbunden werden
- Externes interface ist `v0` (weil man braucht nur den mittleren Knoten um Stern "weiterzubauen")

```
graph Bintree {
  {
    node root;
    graph Bintree as Left;
    graph Bintree as Right;
    edge e1(root, Left.root)
    edge e2(root, Right.root)
  } | {
    node root;
  }
}
```

- Externes interface ist `root`

- **Allgemein aber limitiert**:
  - z.b. Clique: Externes Interface müsste sich ändern je nach Größe

### Graph Query Language

- Erweiterungen, um die Motifs zu einer Query Language zu erweitern:

#### Labels

```
graph G <inproceedings> {
  node v1 <title="Title1", year=2006>;
  node v2 <author name="Schaeler">;
}
```

#### Graph Pattern

```
graph P {
  node v1;
  node v2;
} WHERE v1.name = "A" AND v2.year > 2000;
```

- Wie man sehen kann, muss das Pattern `v1` nicht das `v1` von `G` sein
  - Passende Bindung erst bei pattern match
- Formell: $P(M, F)$
  - $M \coloneqq$ Motif
  - $F \coloneqq$ Filter (Prädikate)
- Graph `G` matched $P(M, F)$ $\iff$ es gibt injektive Funktion $\phi: V(M) \rightarrow V(G)$ sodass:
  1. Für jede Kante $(u, v)$ im Motif $M$, gibt es dazugehörige Kante $(\phi(u), \phi(v))$ in $G$
  2. Alle Prädikate vom Filter $F$ sind erfüllt
  - (injektiv, weil Surjektion nicht notwendig ist (nur Teil des Graphens braucht Mappings für den Match))

#### Graph Algebra

- Menge von Graphen (Graphdatenbank)
- **Unterschied zu Relationaler Algebra**:
  - Selektion miteels Pattern (und somit Motifs)
  - Kompositionsoperator

##### Selektion

- $\sigma_P(C)$
  - $C$: Menge von Graphen
  - $P$: Graph Pattern $P(M, F)$

##### Kartesisches Produkt

- erzeugt neue Graphen, die nicht verbunden sind:
  $$C \times D = \{graph \{ graph G_1, G_2; \} \mid G_1 \in C, G_2 \in D\}$$

##### Komposition

- **Graph Template** besteht aus
  - _Parameter_, als Graph Patterns
  - _Body_, der Ergebnis Graph spezifiziert

- Bsp.:
  - Template Parameter: Pattern $P$:

    ```
    graph P {
      node v1;
      node v2;
    } where v1.author = "A" AND v2.year > 2000
    ```

  - Template Body:

    ```
    T_P = graph {
      node v1 <label=P.v1.name>;
      node v2 <label=P.v2.title>;
      edge e1(v1, v2);
    }
    ```

##### Ausdrucksmächtigkeit

- Vollständigkeit:
  - Selektion
  - Kartesisches Produkt
  - Komposition
  - Vereinigung und Differenz (Graph Pattern kann das)
- Graph Algebra ohne Rekursion = Relationale Algebra
