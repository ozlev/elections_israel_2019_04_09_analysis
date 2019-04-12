import java.util.ArrayList;
import java.util.List;

public class AnalyzedVotingData {
    private final VotingData data;
    private List<String> issues;
    private double validPercent;
    private Double settlementDistanceRatio = null;

    public AnalyzedVotingData(VotingData c) {
        this.data = c;
    }

    public VotingData getData() {
        return data;
    }

    public List<String> getIssues() {
        return issues;
    }

    public double getValidPercent() {
        return validPercent;
    }


    public void setIssues(List<String> issues) {
        this.issues = issues;
    }

    public void setValidPercent(double validPercent) {
        this.validPercent = validPercent;
    }

    public Double getSettlementDistanceRatio() {
        return settlementDistanceRatio;
    }

    public void setSettlementDistanceRatio(Double settlementDistanceRatio) {
        this.settlementDistanceRatio = settlementDistanceRatio;
    }

    public List<Object> toRecord(List<Party> partiesOrder) {
        List<Object> result = new ArrayList<>();
        result.add(data.getSettlement());
        result.add(data.getSymbol());
        result.add(data.getBallotBoxId());
        result.add(data.getSuffrageSize());
        result.add(data.getTotalVotes());
        result.add(data.getDisqualifiedVotes());
        result.add(data.getValidVotes());
        result.add(String.format("%.2f%%", 100d - validPercent));
        result.add(settlementDistanceRatio == null ? "" : String.format("%.3f", settlementDistanceRatio));
        result.add(String.join("\n", issues));
        for (Party p : partiesOrder) {
            result.add(data.getVotesByParty().getOrDefault(p, 0));
        }
        return result;
    }
}
