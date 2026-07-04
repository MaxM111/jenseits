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
- Mögliches Tupel: `Person(Klemens, 32, m)`, `Elternschaft(John, Jeff)`
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
- Horn-Klauseln: Klausel mit maximal ein positiven Literal
  - $\neg p_1 \cup \neg p_2 \cup \neg p_3 \cup u$
- Datalog: Menge von Horn-Klauseln

##### Probleme bei Logischer Anfragesprache

- Erfüllbarkeit (SAT) von Aussagenlogik ist NP-vollständig
- FOL ist nicht entscheidbar
- $\Rightarrow$ Beschränkung auf **Horn-Klauseln**

##### Rekursion in Datalog
