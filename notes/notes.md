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
