import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by USER on 4/12/2019.
 */
public class Analyze {

    private static final String CHARS = "אבגבדהוזחטיךכלםמןנסעףפץצקרשת";
    public static void main(String[] args) {
        try {
            // Read parties
            Map<String, Party> parties = readParties();
            System.out.printf("Parties are: %s%n", parties);

            List<VotingData> polls = new ArrayList<>();
            InputStream is = Analyze.class.getResourceAsStream("/stats/2019_04_12_16_30/expb.csv");
            CSVParser parser = new CSVParser(new InputStreamReader(is, "ISO-8859-8"), CSVFormat.EXCEL.withHeader());

            // Ensure we have all parties
            for (Map.Entry<String, Integer> h : parser.getHeaderMap().entrySet()) {
                if (h.getValue() <= 6) {
                    continue;
                }

                Party party = parties.get(h.getKey());
                if (party == null) {
                    throw new IllegalStateException("Missing party with letter '" + h.getKey() + "'. Please update parties.csv");
                }
            }

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
                polls.add(place);
            }

            System.out.printf("Polls size: %d%n", polls.size());

            // Count national votes.
            VotingData national = countVotes(polls, "*", "*", "*");
            double disqPercent = 100d * national.getDisqualifiedVotes() / national.getTotalVotes();
            System.out.printf("National Invalid percent is %.3f%n", disqPercent);
            double minInvalid = disqPercent * 5;
            System.out.printf("Min Invalid Percent to report is %.3f%n", minInvalid);
            double minValid = 100 - (minInvalid);

            for (VotingData d : polls) {
                List<String> issues = d.getSimpleIssues(minValid);
                if (!issues.isEmpty()) {
                    System.out.printf("***********************************************%n");
                    System.out.printf("Issues in ballot %s (%s)%n", d.getBallotBoxId(), d.getSettlement());
                    for (String issue : issues) {
                        System.out.printf("%s%n", issue);
                    }
                }
            }

            displayVotes(national);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static VotingData countVotes(List<VotingData> polls, String settlement, String settlementSymbol, String ballotBoxId) {

        VotingData base = new VotingData(settlement, settlementSymbol, ballotBoxId, 0, 0, 0, 0, new HashMap<>());

        return polls.stream()
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

    private static String translate(String orig) {
        StringBuilder output = new StringBuilder();
        for (char c : orig.toCharArray()) {
            int i = (int)c;
            if (i >= 224 && i <= 250) {
                int pos = i - 224;
                output.append(CHARS.charAt(pos));
            } else {
                output.append(c);
            }
        }

        return output.toString();
    }
}
