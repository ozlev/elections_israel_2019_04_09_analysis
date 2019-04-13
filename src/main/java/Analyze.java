import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by USER on 4/12/2019.
 */
public class Analyze {

    public static final double MAJOR_PARTY_MIN_RATIO = 0.2;
    public static final double FRINGE_PARTY_MAX_RATIO = 0.01;

    public static void main(String[] args) {
        try {
            // Read parties
            Map<String, Party> parties = readParties();
            System.out.printf("Our %,d Parties are:%n", parties.size());
            for (Party p : parties.values()) {
                System.out.printf("%s (%s)%n", p.getName(), p.getBallot());
            }

            // Read data from all the ballot places
            List<VotingData> ballots = readAllBallots(parties);
            System.out.printf("%nBallots size: %d%n", ballots.size());

            // Count national votes and display
            VotingData national = countVotes(ballots, "*", "*", "*");
            displayVotes(national);

            // Check for issues
            List<AnalyzedVotingData> analysis = ballots.stream().map(AnalyzedVotingData::new).collect(Collectors.toList());
            fillIssues(analysis, national);

            // Generate stats by settlement
            checkBySettlement(analysis);

            // Generate analysis file
            generateReport(analysis, national);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void checkBySettlement(List<AnalyzedVotingData> allBallots) {
        // Group the ballots by settlement.
        Map<String, List<AnalyzedVotingData>> bySettlement = allBallots.stream()
                .collect(Collectors.groupingBy(a -> a.getData().getSymbol()));

        // Run per-settlement calculations
        for (Map.Entry<String, List<AnalyzedVotingData>> e : bySettlement.entrySet()) {
            // Get the ballots for the current settlement
            List<AnalyzedVotingData> cur = e.getValue();
            // Skip places with too few ballots
            if (cur.size() < 5) {
                continue;
            }

            List<VotingData> allData = cur.stream().map(AnalyzedVotingData::getData).collect(Collectors.toList());
            // Get total votes for settlement.
            VotingData settlementTotal = countVotes(allData, "*", "*", "*");

            // Analyze each ballot in this settlement
            for (AnalyzedVotingData analysis : cur) {
                // Check sum of square distances from settlement average for each party (todo: should we generate an average without this ballot?)
                double dist = getSumSquareDist(settlementTotal, analysis.getData());
                if (dist > 0.5) {
                    System.out.printf("%s %s - Dist from settlement average is %.3f%n", analysis.getData().getSettlement(), analysis.getData().getBallotBoxId(), dist);
                }
                analysis.setSettlementDistanceRatio(dist);
            }
        }
    }

    /**
     * Get the sum of square distances between the normalized votes for each party between two {@link VotingData} items
     * @param v1 voting data 1
     * @param v2 voting data 2
     * @return sum of square distances between the voting ratio for each party
     */
    private static double getSumSquareDist(VotingData v1, VotingData v2) {
        Map<Party, Double> ballotNorm = v2.getNormalizedVotesByParty();
        Map<Party, Double> settlementNorm = v1.getNormalizedVotesByParty();
        if (!ballotNorm.keySet().equals(settlementNorm.keySet())) {
            System.out.printf("Mismatching keys between %s and its ballot %s:%n%s%n%s%n",
                    v2.getSettlement(), v2.getBallotBoxId(), ballotNorm.keySet(), settlementNorm.keySet());
        }
        double dist = 0d;
        for (Party p : ballotNorm.keySet()) {
            Double d1 = ballotNorm.get(p);
            Double d2 = settlementNorm.get(p);
            dist += Math.pow(d1 - d2, 2);
        }
        return dist;
    }

    private static void fillIssues(List<AnalyzedVotingData> allBallots, VotingData national) {
        for (AnalyzedVotingData analysis : allBallots) {
            VotingData data = analysis.getData();
            List<String> issues = data.getSimpleIssues();

            issues.addAll(checkForSwitches(data, national));

            analysis.setIssues(issues);
            double validPercent = validPercent(data);
            analysis.setValidPercent(validPercent);
            if (!issues.isEmpty()) {
                System.out.printf("***********************************************%n");
                System.out.printf("Issues in ballot %s (%s)%n", data.getBallotBoxId(), data.getSettlement());
                for (String issue : issues) {
                    System.out.printf("%s%n", issue);
                }


            }
        }
    }

    /**
     * Check for suspicious switches of all votes tallied to a party with another
     * @param ballot current ballot voting
     * @param national national voting data
     * @return list of issues, or empty list if there are none
     */
    private static List<String> checkForSwitches(VotingData ballot, VotingData national) {
        // todo: extract the major and fringe parties only once to improve performance (mostly irrelevant, since this takes very little time)
        // Get the 'fringe' parties (anything with less than 1% of the votes nationally), that have a high percentage in this ballot
        List<Map.Entry<Party, Double>> highFringe = ballot.getNormalizedVotesByParty().entrySet().stream()
                .filter(e -> e.getValue() > 0.05 && national.getNormalizedVotesByParty().get(e.getKey()) < FRINGE_PARTY_MAX_RATIO)
                .collect(Collectors.toList());

        // Get the 'major' parties (anything with more than 20% of the votes nationally) that have a low percentage in this ballot
        List<Map.Entry<Party, Double>> lowMajor = ballot.getNormalizedVotesByParty().entrySet().stream()
                .filter(e -> e.getValue() < 0.001 && national.getNormalizedVotesByParty().get(e.getKey()) > MAJOR_PARTY_MIN_RATIO)
                .collect(Collectors.toList());

        List<String> result = new ArrayList<>();
        // If we have a possible switch, show it.
        if (!highFringe.isEmpty() && !lowMajor.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            highFringe.forEach(e -> addPartyVotes(ballot, sb, e));
            lowMajor.forEach(e -> addPartyVotes(ballot, sb, e));
            result.add(sb.toString());
        }

        return result;
    }

    private static StringBuilder addPartyVotes(VotingData ballot, StringBuilder sb, Map.Entry<Party, Double> e) {
        return sb.append(e.getKey().getName() + "=" + ballot.getVotesByParty().get(e.getKey()) + " ; ");
    }

    private static double validPercent(VotingData data) {
        return 100d * data.getValidVotes() / data.getTotalVotes();
    }

    private static List<VotingData> readAllBallots(Map<String, Party> parties) throws IOException {
        List<VotingData> result = new ArrayList<>();
        InputStream is = Analyze.class.getResourceAsStream("/stats/2019_04_12_16_30/expb.csv");
        CSVParser parser = new CSVParser(new InputStreamReader(is, "ISO-8859-8"), CSVFormat.EXCEL.withHeader());

        // Ensure we have all parties
        verifyPartyList(parties, parser);

        for (CSVRecord record : parser) {
            // Read party votes
            Map<Party, Integer> votes = new HashMap<>();
            for (Party p : parties.values()) {
                Integer v = Integer.valueOf(record.get(p.getBallot()));
                votes.put(p, v);
            }

            VotingData place = new VotingData(record.get(0),
                    record.get(1), record.get(2),
                    Integer.valueOf(record.get(3)),
                    Integer.valueOf(record.get(4)),
                    Integer.valueOf(record.get(5)),
                    Integer.valueOf(record.get(6)),
                    votes
            );
            result.add(place);
        }
        return result;
    }

    private static void verifyPartyList(Map<String, Party> parties, CSVParser parser) {
        for (Map.Entry<String, Integer> h : parser.getHeaderMap().entrySet()) {
            if (h.getValue() <= 6) {
                continue;
            }

            Party party = parties.get(h.getKey());
            if (party == null) {
                throw new IllegalStateException("Missing party with letter '" + h.getKey() + "'. Please update parties.csv");
            }
        }
    }

    /**
     * Count the total voting data from a list of ballots (using optional filters)
     * @param allBallots ballots to add
     * @param settlement settlement name (for the returned {@link VotingData} item)
     * @param settlementSymbol settlement symbol wildcard (if not null, only ballots from this settlement are counted)
     * @param ballotBoxId ballot box symbol wildcard (if not null, only ballots matching this are counted)
     * @return summary {@link VotingData} for all matching ballots
     */
    @SuppressWarnings("SameParameterValue")
    private static VotingData countVotes(List<VotingData> allBallots, String settlement, String settlementSymbol, String ballotBoxId) {
        VotingData base = new VotingData(settlement, settlementSymbol, ballotBoxId, 0, 0, 0, 0, new HashMap<>());
        return allBallots.stream()
                .filter(p -> settlementSymbol == null || p.getBallotBoxId().matches(toWildcard(settlementSymbol)))
                .filter(p -> ballotBoxId == null || p.getBallotBoxId().matches(toWildcard(ballotBoxId)))
                .reduce(base, VotingData::combine);
    }

    private static String toWildcard(String pattern) {
        return pattern.replace("?", ".?").replace("*", ".*?");
    }

    private static void displayVotes(VotingData data) {
        System.out.printf("%-20s: %s (%s)%n", "Settlement", data.getSettlement(), data.getSymbol());
        System.out.printf("%-20s: %s%n", "Ballot", data.getBallotBoxId());
        System.out.printf("%-20s: %,d%n", "Suffrage Size", data.getSuffrageSize());
        System.out.printf("%-20s: %,d%n", "Total Votes", data.getTotalVotes());
        System.out.printf("%-20s: %,d%n", "Disqualified Votes", data.getDisqualifiedVotes());
        System.out.printf("%-20s: %,d%n", "Valid Votes", data.getValidVotes());

        LinkedHashMap<Party, Integer> byVotes = data.getVotesByParty().entrySet().stream()
                .sorted(Map.Entry.<Party, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        System.out.printf("Votes by party:%n========================%n");
        for (Map.Entry<Party, Integer> entry : byVotes.entrySet()) {
            System.out.printf("%-30s: %,-12d%n", entry.getKey().getName(), entry.getValue());
        }
    }

    private static Map<String, Party> readParties() throws IOException {
        InputStream is = Analyze.class.getResourceAsStream("/parties.csv");
        is =new BOMInputStream(is);
        CSVParser parser = new CSVParser(new InputStreamReader(is, StandardCharsets.UTF_8), CSVFormat.EXCEL.withHeader());
        Map<String, Party> result = new HashMap<>();
        for (CSVRecord record : parser) {
            String name = record.get("Party");
            String letters = record.get("Ballot");
            Party old = result.put(letters, new Party(name, letters));
            if (old != null) {
                throw new IllegalStateException("Duplicate parties with ballot '" + letters + "'");
            }
        }

        return result;
    }

    private static void generateReport(List<AnalyzedVotingData> analysis, VotingData national) throws IOException {
        System.out.printf("Saving report%n");
        File output = new File("analysis/all_ballot_places.csv");
        //noinspection ResultOfMethodCallIgnored
        output.getParentFile().mkdirs();

        List<String> headers = new ArrayList<>();
        headers.add("יישוב");
        headers.add("סמל");
        headers.add("קלפי");
        headers.add("בעלי זכות הצבעה");
        headers.add("הצביעו");
        headers.add("קולות פסולים");
        headers.add("קולות כשרים");
        headers.add("אחוז פסולים");
        headers.add("סכום מרחקים מממוצע היישוב");
        headers.add("הערות");

        LinkedHashMap<Party, Integer> byVotes = national.getVotesByParty().entrySet().stream()
                .sorted(Map.Entry.<Party, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        List<Party> partyOrder = new ArrayList<>(byVotes.keySet());
        for (Party p : byVotes.keySet()) {
            headers.add(p.getName() + " - " + p.getBallot());
        }

        @SuppressWarnings("ToArrayCallWithZeroLengthArrayArgument")
        String[] headersArray = headers.toArray(new String[headers.size()]);
        FileWriter out = new FileWriter(output);
        out.write(ByteOrderMark.UTF_BOM);
        CSVPrinter printer = new CSVPrinter(out, CSVFormat.EXCEL.withHeader(headersArray));
        AnalyzedVotingData nationalAnalysis = new AnalyzedVotingData(national);
        nationalAnalysis.setValidPercent(validPercent(national));
        nationalAnalysis.setIssues(Collections.emptyList());
        printer.printRecord(nationalAnalysis.toRecord(partyOrder));
        for (AnalyzedVotingData pollAnalysis : analysis) {
            printer.printRecord(pollAnalysis.toRecord(partyOrder));
        }
        printer.close();
    }

}
