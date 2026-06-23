package jenseits;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.xml.parsers.SAXParserFactory;

import jenseits.DBLPHandler.VenueRule;
import jenseits.Jenseits03.XPathAxis;
import jenseits.util.Logger;

public class Jenseits03Benchmark {
    private static final Path DEFAULT_SOURCE = Path.of("dblp.xml");
    private static final int DEFAULT_WARMUP_RUNS = 1;
    private static final int DEFAULT_MEASURED_RUNS = 5;
    private static final int[] DEFAULT_SCALE_FACTORS = { 1, 2, 4, 8 };

    @FunctionalInterface
    private interface TimedQuery {
        int run() throws Exception;
    }

    private record BenchmarkContext(long articleId, long yearId, XPathAxis siblingAxis) {
    }

    private record Measurement(double averageMillis, int resultSize) {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("jdk.xml.entityExpansionLimit", "1000000000");
        System.setProperty("jdk.xml.totalEntitySizeLimit", "1000000000");
        System.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "1000000000");

        var source = args.length > 0 ? Path.of(args[0]) : DEFAULT_SOURCE;
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Benchmark source XML does not exist: " + source);
        }

        var venueRules = benchmarkVenueRules();
        try (var logger = new Logger("logs", "phase3_benchmark.csv")) {
            logger.log(
                    "dataset",
                    "venue_count",
                    "node_count",
                    "edge_count",
                    "approach",
                    "axis",
                    "context_id",
                    "sibling_axis",
                    "result_size",
                    "avg_ms",
                    "runs");

            for (var scaleFactor : DEFAULT_SCALE_FACTORS) {
                int venueCount = Math.min(venueRules.size(), DBLPHandler.defaultVenueRules().size() * scaleFactor);
                var activeRules = List.copyOf(venueRules.subList(0, venueCount));
                var dataset = "venues_" + venueCount;

                System.out.printf("Parsing %s with %d venue rules...%n", source, venueCount);
                var root = parse(source, activeRules);
                var context = selectBenchmarkContext(root);

                try (var project = new Jenseits03()) {
                    root.toEdgeModel(project.getConn());
                    createEdgeIndexes(project);
                    long nodeCount = project.countTuples("Node");
                    long edgeCount = project.countTuples("Edge");

                    logMeasurement(logger, dataset, venueCount, nodeCount, edgeCount, "edge", "ancestor",
                            context.articleId(), context.siblingAxis(),
                            measure(() -> project.getAncestors(context.articleId()).size()));
                    logMeasurement(logger, dataset, venueCount, nodeCount, edgeCount, "edge", "descendant",
                            context.yearId(), context.siblingAxis(),
                            measure(() -> project.getDescendants(context.yearId()).size()));
                    logMeasurement(logger, dataset, venueCount, nodeCount, edgeCount, "edge",
                            context.siblingAxis().name(), context.articleId(), context.siblingAxis(),
                            measure(() -> runEdgeSiblingQuery(project, context)));

                    project.importAccel(root);
                    createAccelIndexes(project);
                    long accelCount = project.countTuples("accel");

                    logMeasurement(logger, dataset, venueCount, accelCount, edgeCount, "xpath_accel", "ancestor",
                            context.articleId(), context.siblingAxis(),
                            measure(() -> project.xpath(context.articleId(), XPathAxis.Ancestor).size()));
                    logMeasurement(logger, dataset, venueCount, accelCount, edgeCount, "xpath_accel", "descendant",
                            context.yearId(), context.siblingAxis(),
                            measure(() -> project.xpath(context.yearId(), XPathAxis.Descendant).size()));
                    logMeasurement(logger, dataset, venueCount, accelCount, edgeCount, "xpath_accel",
                            context.siblingAxis().name(), context.articleId(), context.siblingAxis(),
                            measure(() -> project.xpath(context.articleId(), context.siblingAxis()).size()));

                    int treeHeight = Jenseits03.height(root);

                    logMeasurement(logger, dataset, venueCount, accelCount, edgeCount, "xpath_reduced", "ancestor",
                            context.articleId(), context.siblingAxis(),
                            measure(() -> project.xpathReduced(context.articleId(), XPathAxis.Ancestor, treeHeight)
                                    .size()));
                    logMeasurement(logger, dataset, venueCount, accelCount, edgeCount, "xpath_reduced", "descendant",
                            context.yearId(), context.siblingAxis(),
                            measure(() -> project.xpathReduced(context.yearId(), XPathAxis.Descendant, treeHeight)
                                    .size()));
                    logMeasurement(logger, dataset, venueCount, accelCount, edgeCount, "xpath_reduced",
                            context.siblingAxis().name(), context.articleId(), context.siblingAxis(),
                            measure(() -> project.xpathReduced(context.articleId(), context.siblingAxis(), treeHeight)
                                    .size()));

                    project.importAccelOneAxis(root);
                    createOneAxisIndexes(project);
                    long oneAxisCount = project.countTuples("accel");

                    logMeasurement(logger, dataset, venueCount, oneAxisCount, edgeCount, "xpath_one_axis",
                            "descendant", context.yearId(), context.siblingAxis(),
                            measure(() -> project.xpathDescendantOneAxis(context.yearId()).size()));

                    logger.flush();
                }
            }
        }
    }

    private static Node parse(Path source, List<VenueRule> venueRules) throws Exception {
        var factory = SAXParserFactory.newInstance();
        var parser = factory.newSAXParser();
        var handler = new DBLPHandler(venueRules);
        parser.parse(source.toFile(), handler);
        return handler.getTree();
    }

    private static List<VenueRule> benchmarkVenueRules() {
        var rules = new ArrayList<>(DBLPHandler.defaultVenueRules());
        rules.add(new VenueRule("pods", "conf/pods/"));
        rules.add(new VenueRule("edbt", "conf/edbt/"));
        rules.add(new VenueRule("tods", "journals/tods/"));
        rules.add(new VenueRule("kdd", "conf/kdd/"));
        rules.add(new VenueRule("cikm", "conf/cikm/"));
        rules.add(new VenueRule("www", "conf/www/"));
        rules.add(new VenueRule("iswc", "conf/semweb/"));
        rules.add(new VenueRule("dasfaa", "conf/dasfaa/"));
        rules.add(new VenueRule("ssdbm", "conf/ssdbm/"));
        rules.add(new VenueRule("icdm", "conf/icdm/"));
        rules.add(new VenueRule("caise", "conf/caise/"));
        rules.add(new VenueRule("sigir", "conf/sigir/"));
        rules.add(new VenueRule("tkde", "journals/tkde/"));
        rules.add(new VenueRule("dke", "journals/dke/"));
        rules.add(new VenueRule("cacm", "journals/cacm/"));
        rules.add(new VenueRule("jcss", "journals/jcss/"));
        rules.add(new VenueRule("icdt", "conf/icdt/"));
        rules.add(new VenueRule("stoc", "conf/stoc/"));
        rules.add(new VenueRule("soda", "conf/soda/"));
        rules.add(new VenueRule("aaai", "conf/aaai/"));
        rules.add(new VenueRule("ijcai", "conf/ijcai/"));
        return rules;
    }

    private static BenchmarkContext selectBenchmarkContext(Node root) {
        Node firstYearWithPublications = null;
        Node siblingContext = null;
        XPathAxis siblingAxis = new Random(42).nextBoolean()
                ? XPathAxis.FollowingSibling
                : XPathAxis.PrecedingSibling;

        for (var venue : root.getChildren()) {
            for (var year : venue.getChildren()) {
                var publications = year.getChildren();
                if (publications.isEmpty()) {
                    continue;
                }
                if (firstYearWithPublications == null) {
                    firstYearWithPublications = year;
                }
                if (siblingAxis == XPathAxis.FollowingSibling && publications.size() > 1) {
                    siblingContext = publications.get(0);
                    break;
                }
                if (siblingAxis == XPathAxis.PrecedingSibling && publications.size() > 1) {
                    siblingContext = publications.get(publications.size() - 1);
                    break;
                }
            }
            if (siblingContext != null) {
                break;
            }
        }

        if (firstYearWithPublications == null) {
            throw new IllegalStateException("No benchmark year node found.");
        }
        if (siblingContext == null) {
            siblingContext = firstYearWithPublications.getChildren().get(0);
        }

        return new BenchmarkContext(siblingContext.getID(), firstYearWithPublications.getID(), siblingAxis);
    }

    private static void createEdgeIndexes(Jenseits03 project) throws Exception {
        var statement = project.getConn().createStatement();
        statement.execute("CREATE INDEX IF NOT EXISTS edge_from_idx ON Edge(from_)");
        statement.execute("CREATE INDEX IF NOT EXISTS edge_to_idx ON Edge(to_)");
        statement.execute("CREATE INDEX IF NOT EXISTS node_id_idx ON Node(id)");
        statement.execute("CLUSTER Edge USING edge_from_idx");
        project.getConn().commit();
    }

    private static void createAccelIndexes(Jenseits03 project) throws Exception {
        var statement = project.getConn().createStatement();
        statement.execute("CREATE INDEX IF NOT EXISTS accel_id_idx ON accel(id)");
        statement.execute("CREATE INDEX IF NOT EXISTS accel_parent_idx ON accel(parent)");
        statement.execute("CREATE INDEX IF NOT EXISTS accel_pre_post_idx ON accel(pre, post)");
        statement.execute("""
                CREATE INDEX IF NOT EXISTS accel_point_gist_idx
                ON accel USING gist (point(pre::double precision, post::double precision))
                """);
        project.getConn().commit();
    }

    private static void createOneAxisIndexes(Jenseits03 project) throws Exception {
        var statement = project.getConn().createStatement();
        statement.execute("CREATE INDEX IF NOT EXISTS one_axis_pre_idx ON accel(pre)");
        statement.execute("CREATE INDEX IF NOT EXISTS one_axis_id_idx ON accel(id)");
        statement.execute("CLUSTER accel USING one_axis_pre_idx");
        project.getConn().commit();
    }

    private static int runEdgeSiblingQuery(Jenseits03 project, BenchmarkContext context) throws Exception {
        if (context.siblingAxis() == XPathAxis.FollowingSibling) {
            return project.getFollowingSiblings(context.articleId()).size();
        }
        return project.getPrecedingSiblings(context.articleId()).size();
    }

    private static Measurement measure(TimedQuery query) throws Exception {
        int resultSize = query.run();
        for (int i = 0; i < DEFAULT_WARMUP_RUNS; i++) {
            query.run();
        }

        long start = System.nanoTime();
        for (int i = 0; i < DEFAULT_MEASURED_RUNS; i++) {
            resultSize = query.run();
        }
        long end = System.nanoTime();

        double averageMillis = (end - start) / 1_000_000.0 / DEFAULT_MEASURED_RUNS;
        return new Measurement(averageMillis, resultSize);
    }

    private static void logMeasurement(
            Logger logger,
            String dataset,
            int venueCount,
            long nodeCount,
            long edgeCount,
            String approach,
            String axis,
            long contextId,
            XPathAxis siblingAxis,
            Measurement measurement) throws Exception {
        logger.log(
                dataset,
                String.valueOf(venueCount),
                String.valueOf(nodeCount),
                String.valueOf(edgeCount),
                approach,
                axis,
                String.valueOf(contextId),
                siblingAxis.name(),
                String.valueOf(measurement.resultSize()),
                String.format("%.3f", measurement.averageMillis()),
                String.valueOf(DEFAULT_MEASURED_RUNS));
    }
}
