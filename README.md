## Israeli Election 2019  Analysis
Some code to analyze data from the Israeli General Elections held on April 9, 2019

Feel free to add more tests and analyses to it, just don't cheat!

## How to Use
The expb.csv file contains the data from the Central Election Committee site https://votes21.bechirot.gov.il

The class **Analyze**:
* Reads this data (each ballot is kept as a VotingData item)
* Runs some basic analyses and verifications on it.
* Reports suspicious issues to the console
* Generates an output CSV report with the votes from each ballot and the analyzed data for it

Other classes:
* VotingData - cotnains voting data (from a ballot, settlement, national, etc.)
* AnalyzedVotingData - contains VotingData plus some analyses on it
* Party - contains name and ballot id for a party (read from parties.csv on startup)

## Please Note
The "expb.csv" and "expc.csv" files, which originated in the official Election Site (https://votes21.bechirot.gov.il), use character encoding "ISO-8859-8" for the Hebrew characters (located in ASCII values 224-250).

I have decided not to use the same encoding for *all other* inputs and output, but rather use UTF-8 for easier integration with other applications.

