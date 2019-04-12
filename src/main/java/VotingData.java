import java.util.*;

@SuppressWarnings("WeakerAccess")
public class VotingData {
    private final String settlement;
    private final String symbol;
    private final String ballotBoxId;
    private final int suffrageSize;
    private final int totalVotes;
    private final int disqualifiedVotes;
    private final int validVotes;

    private final Map<Party, Integer> votesByParty;

    // The same data as votes by party, but normalized so that the total votes is 1
    private final Map<Party, Double> normalizedVotesByParty;

    public VotingData(String settlement, String symbol, String ballotBoxId, int suffrageSize, int totalVotes, int disqualifiedVotes, int validVotes, Map<Party, Integer> votesByParty) {
        this.settlement = settlement;
        this.symbol = symbol;
        this.ballotBoxId = ballotBoxId;
        this.suffrageSize = suffrageSize;
        this.totalVotes = totalVotes;
        this.disqualifiedVotes = disqualifiedVotes;
        this.validVotes = validVotes;
        this.votesByParty = votesByParty;


        double totalByParty = votesByParty.values().stream().mapToInt(Integer::intValue).sum();
        normalizedVotesByParty = new HashMap<>();
        if (totalByParty > 0){
            for (Map.Entry<Party, Integer> entry : votesByParty.entrySet()) {
                normalizedVotesByParty.put(entry.getKey(), entry.getValue() / totalByParty);
            }
        }
    }

    public String getSettlement() {
        return settlement;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getBallotBoxId() {
        return ballotBoxId;
    }

    public int getSuffrageSize() {
        return suffrageSize;
    }

    public int getTotalVotes() {
        return totalVotes;
    }

    public int getDisqualifiedVotes() {
        return disqualifiedVotes;
    }

    public int getValidVotes() {
        return validVotes;
    }

    public Map<Party, Integer> getVotesByParty() {
        return Collections.unmodifiableMap(votesByParty);
    }

    public Map<Party, Double> getNormalizedVotesByParty() {
        return Collections.unmodifiableMap(normalizedVotesByParty);
    }

    public static VotingData combine(VotingData v1, VotingData v2) {
        Map<Party, Integer> aggregatedVotes = new HashMap<>();
        addPartyVotes(v1, aggregatedVotes);
        addPartyVotes(v2, aggregatedVotes);

        return new VotingData(v1.settlement, v1.symbol, v1.ballotBoxId,
                v1.suffrageSize + v2.suffrageSize,
                v1.totalVotes + v2.totalVotes,
                v1.disqualifiedVotes + v2.disqualifiedVotes,
                v1.validVotes + v2.validVotes,
                aggregatedVotes
                );

    }

    private static void addPartyVotes(VotingData v1, Map<Party, Integer> votes) {
        for (Map.Entry<Party, Integer> entry : v1.getVotesByParty().entrySet()) {
            Party party = entry.getKey();
            Integer v = entry.getValue();
            votes.put(party, votes.getOrDefault(party, 0) + v);
        }
    }

    /**
     * Check the voting data for obvious incongruencies (such as total not equal to sum of votes)
     * @return list of issues (empty if all is fine)
     */
    public List<String> getSimpleIssues() {
        List<String> issues = new ArrayList<>();
        // Double envelopes don't have a suffrage size...
        if (suffrageSize > 0 && totalVotes > suffrageSize) {
            issues.add(String.format("Voting over 100%%. Suffrage size: %,d ; Total votes: %,d", suffrageSize, totalVotes));
        }

        if (totalVotes != disqualifiedVotes + validVotes) {
            issues.add(String.format("Mismatch total votes. Valid + Disqualified != Total: %,d + %,d != %,d", validVotes, disqualifiedVotes, totalVotes));
        }

        int totalByParty = votesByParty.values().stream().mapToInt(Integer::intValue).sum();
        if (totalByParty != validVotes) {
            issues.add(String.format("Total by party != total (%,d != %,d)", totalByParty, validVotes));
        }

        return issues;
    }
}
