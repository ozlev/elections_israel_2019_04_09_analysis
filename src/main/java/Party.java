public class Party {

    private final String name;
    private final String ballot;

    public Party(String name, String ballot) {
        this.name = name;
        this.ballot = ballot;
    }

    public String getName() {
        return name;
    }

    public String getBallot() {
        return ballot;
    }

    @Override
    public String toString() {
        return name + " (" + ballot + ")";
    }
}
