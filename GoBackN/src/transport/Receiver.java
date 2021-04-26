package transport;
//Candidate Number: 184676

public class Receiver extends NetworkHost {
    //Values that won't change during execution, so declared as constant.
    //-1 is used to mean null value as 0 is the min.
    private final static int MINSEQUENCENUMBER = 0;
    private final static int MAXSEQUENCENUMBER = 49;
    
    //The sequence number an incoming packet should have to be accepted.
    private int expectedSequenceNumber;
    
    //Increase the expectedSequenceNumber, deals with wraparound, used after a packet is accepted.
    private void incrementExpectedSequenceNumber() {
        expectedSequenceNumber += 1;
        if (expectedSequenceNumber > MAXSEQUENCENUMBER) {
            expectedSequenceNumber = MINSEQUENCENUMBER;
        }
    }
    
    //Previous sequence number is the highest ACK sent so far, used when a duplicate packet is received.
    private int getPreviousSequenceNumber() {
        int previousSequenceNumber = expectedSequenceNumber - 1;
        if (previousSequenceNumber < MINSEQUENCENUMBER) {
            previousSequenceNumber = MAXSEQUENCENUMBER;
        }
        return previousSequenceNumber;
    }
    
    //Gets the checksum for a string by summing the ASCII value of every character.
    private int checksumString (String string) {
        int runningSum = 0;
        for (char ch : string.toCharArray()) {
            runningSum += (int) ch;
        }
        return runningSum;
    }
    
    /**
     * Calculates the checksum of a received packet and compares it to the packet's stated checksum.
     * @param packet the packet to be checked.
     * @return false is the packet is definitely corrupted, true otherwise.
     */
    private boolean validateChecksum (Packet p) {
        int calculatedChecksum = p.getSeqnum() + p.getAcknum() + checksumString(p.getPayload());
        return calculatedChecksum == p.getChecksum();
    }
    
    //Constructor.
    public Receiver(int entityName) {
        super(entityName);
    }
    
    //Initialise all non-constant class variables.
    @Override
    public void init() {
        expectedSequenceNumber = MINSEQUENCENUMBER;
    }
    
    /**
     * Trigger when a packet is received from the sender (i.e as a result of udtSend()).
     * @param packet The received packet, possibly corrupt.
     */
    @Override
    public void input(Packet packet) {
        //Check that the packet isn't corrupt
        if (validateChecksum(packet)) {
            int seqNumber = packet.getSeqnum();
            Packet outputPacket;
            //Check it's the expected packet, arriving in order
            if (seqNumber == expectedSequenceNumber) {
                deliverData(packet.getPayload());
                int packetSeqNum = -1;
                int packetAckNum = seqNumber;
                String packetData = "";
                int packetChecksum = packetSeqNum + packetAckNum;
                outputPacket = new Packet(packetSeqNum, packetAckNum, packetChecksum, packetData);
                incrementExpectedSequenceNumber();
            }
            //Otherwise it's an old packet, so send the highest ACK so far
            else {
                //Fill output packet
                int packetSeqNum = -1;
                int packetAckNum = getPreviousSequenceNumber();
                String packetData = "";
                int packetChecksum = packetSeqNum + packetAckNum;
                outputPacket = new Packet(packetSeqNum, packetAckNum, packetChecksum, packetData);
            }
            udtSend(outputPacket);
        }
        //By design, corrupt packets are ignored and treated as lost
    }

}
