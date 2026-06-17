## In-memory Databases Notes
TID:
  Tuple identifier
    - Bei SELECT wird also [TID] zurückgegeben
    - Bei Join wird [(TID, TID)] zurückgegeben

Tuple-at-a-time wird oft parallelisiert, aber dann arbeiten Operatoren verschränkt => Instruction cache misses und großer ausführungszustand, sodass data caches misses geschehen

90% Überlast bei Ausführung von bsp query war früher ok, weil die festplatte ohnehin so langsam war. Jetzt ist das aber nicht mehr so.

Slide 40: Du musst dir nicht alle Ursachen merken, sondern eher nur das grobe Problem.

OLAP Standardarchitektur:
  - Wir brauchen keine Transaktionsverwaltung, wenn eh primär nur gelesen wird (Transaktionen sind eher wichtig, wenn man gegenseitig die Datenbank modifiziert)
Aber wenn man kurzfristig wichtiges updaten möchte, gibt es Schwierigkeiten -> Alternative: Delta Store

Delta Merge in SAP HANA
  - dient dazu, dass kein exclusive lock vom main store notwendig ist bei Synchronisation

Wörterbuchkodierung: Für OLAP gedacht (Lesen), weil der Insertion müsste man dictionary updaten

HyPer: Vereinfacht Transaktionen, indem sie sequentiell sind (weil main memory sowieso schnell ist)

## Datenmodelle Notes

Bei Folie 8 muss man nicht den ausgegrauten Typkonstruktor wissen. Steht da aus Formalitäts/Vollständigkeitsgründen.

Die Domäne einer Relation is das Produkt der Domänen seiner Attribute.

$$dom(R) = \prod_{a \in A} dom(a)$$

- Polymorphe Konsistenzbedinungen
  - Primärschlüssel/Fremdschlüssel

- Was muss ein Datenmodell können?
  - Menge der Zustände beschreiben können
  - Menge der Operatoren beschreiben können
  - z.b. Java: elementare Typen sind die primitive Datentypen

### Relation vs Dokument
- Dokument hat Struktur und Daten (= Text) zusammen
  - im Gegensatz dazu ist z.b. bei relationale DB Struktur (= Schema) getrennt von Daten (= pages mit Daten)

- wohlgeformt vs gültig 
  - wohlgeformt: XML syntax ist korrekt
  - gültig: Semantik des Schema's (je nachdem wofür XML verwendet wird) (fordert, dass es wohlgeformt ist)
    - semistrukturiert: Man muss nicht so ein Schema festlegen

- XML Schema-definitionsslide: Man muss nicht das Schema merken. Wichtig is nur:
  - Was sind atomare Typen
    - \#PCDATA, \#CDATA
  - Was sind Typ Konstruktoren
    - Es gibt \|, \*, \+, \, $\dots$

- In XML gibt es Elemente UND Attribute. Da Relationen nur Attribute haben, kann man praktisch beliebig auswählen, ob sie in XML
   als Element und Attribut behandelt werden. (Darf man frei wählen)

- Sei sparsam mit \| (oder) bei Erstellung von XML repräsentation
  - Es ist ineffizient, da es mehr Möglichekeiten gibt, die man überprüfen muss
  - Wenn man mehr und mehr verwendet, dann wird die Struktur nicht mehr im Griff behalten

- Wie können XML-Dokumente aufgebaut sein
  - Konstruktoren relevant, Syntax eher nicht (ist ihm nicht so wichtig)
  - XML-Konzepte wie atomare Typen, Listenkomposition
  - Vergleich mit relationalem Modell

- Structured Consciousness
  - z.b. wenn man weiß, dass ein gewisses Element genau einmal vorkommt, dann muss man nicht auf mehrere überprüfen
  
- Abgeschlossenheit:
  - Relationale Algebra ist abgeschlossen (jeder Operator nimmt und resultiert in einer neuen Relation)
  - um das ähnlich zu machen, wird bei xpath-artiges oft ein Baum zurückgegeben, der einen root "result" hat, dessen Kinder die Ergebnisse sind.

- XML oft langsamer als relationale Algebra, weil sie mehr können und somit komplexer sind.

## XPath
- Pfadausdrücke für XML
- Scheinbar wichtig: Zwischen relativ und absolute Pfadausdrücke unterscheiden können
- Syntax muss nicht gekonnt werden, nur was es kann
- Wichtig ist Folie 26 "Achsen": Man soll die Begriffe verstehen können
- Edge Modell ist schlecht, weil das Aufsplitten zu sehr hohen Kosten bei der Berechnung von Joins führt
- R in R-Baum steht für Rectangle
- MBR = Minimum Bounded Rectangle
  - zeichnen minimales aber noch bounded Rectangle um mehrere Rectangles um es einzuordnen
- Implementierung von R-Baum in DBMS heißt "GiST"="Generalised Search Tree" (Generalisierung von der geometrischen Form, z.b. statt Rechtecke nimmt man Kreise)
- Wenn man Knoten von XMl-Baum mit pre-und postorder annotiert, können die Order-werte als Koordinaten verwendet werden (siehe Abb. in Folien)
- Vektorrepräsentation ist empfohlen
  - man braucht nicht die letzten zwei "fields" kind(v) und name(v), so wäre es vollständig, aber für die Aufgabe erwartet nur das Konzept (also pre-/postorder und parent)
- Implementiere in Aufgabe erst mit Infinity und 0 als bounds
  - Später wird dann eine Optimierung implementiert

- Prüfungsrelevant:
  - Edge Modell erklären/manuell überführen
  - Annotationen erklären können
  - Für gute Note, eine der Optimierungen (meist wählbar)

## Graphen
- Wichtige Fragen: Erreichbarkeit (Pfade) & Zentralität. Fokus wird auf Zentralität gelegt.
- Informationsbedürfnisse sind möglich, aber nicht schön und nicht effizient (folie 5)
- InDegree
  - Relevanz: Bsp: Erfolg eines Videos ist Anzahl der Aufrufe
  - Warum nicht genügend?: Leicht fälschbar (likes kaufen, Bots...), aber erhöht nicht die Wichtigkeit.
    - auch: Zitieren in Wissenschaft: Z.b. es könnte wichtig sein wer einen zitiert als wie viele
- PageRank
  - (Algorithmus der Google groß gemacht hat, für Suchmaschinen)
  - empfohlener approach
  - nur gerichtete Graphen
  - zu Beginn initialisiere Page Rank mit 1/n für n Knoten, sodass die Summe der PageRanks 1 ergibt.
  - d := Dämpfungs-faktor (z.b. 0.8), N := |V| => (1 - d) / N erhöht Page rank ein wenig (damit er niemals 0 ist, denn wenn der Knoten keine eingehenden Kanten hat, dann würde er sosnst Pagerank = 0 haben)
  - PR_j = Page Rank von Knoten j, C_j := ausgehende Kanten von Knoten j => Formel macht Knoten die exklusiv von anderen Knoten erreichbar wichtig..
  - Erkläre bei Prüfung PageRank an einem Bsp Graph mit einer Runde und was dieser Dämpfungsfaktor ist

### Proximity Prestige
- Zähler: Wir normalisieren die Influence Domain $I$. Da für maximales $I$ gilt $|I| = n - 1$ für $n$ Knoten, wird $I$ mit $n-1$ normalisiert.
- Nenner: Durschnittliche minimale Distanzen zwischen Knoten $i$ und $j \in I$. (Average durch die Summe und dann durch $|I|$ dividieren).
- Wert liegt in $(0, 1]$, wobei höher eine höhere Proximity Prestige ist
- Wenn $I = {}$, dann ist Proximity Prestige nicht berechenbar
- Bsp: Potential Congestion Erkennung

### Betweeness Centrality
- Zähler: Wenn kein Pfad mit $i$, dann wird der Bruch 0 sein.
- $j < k$ weil es ungerichtet ist (?)

- Wann sind Proximity Prestige und Betweeness Centrality anders?
  - Betrachte einen Graphen mit 4 Knoten, einer in der Mitte und die anderen drei haben eine Kante zur Mitte
    - Dann ist Proximity Prestige und Betweeness Centrality dasselbe
    - Wenn aber eine Clique daraus gemacht wird (Kanten zwischen den drei äußeren Knoten), dann ist Betweeness Centrality eher klein, weil der kürzeste Pfad zwischen $j$ und $k$ nicht mehr über $i$ geht

## Mustererkennung mit Motifs für Graphen

- Externes Interface := Folie 21 blau markiert
