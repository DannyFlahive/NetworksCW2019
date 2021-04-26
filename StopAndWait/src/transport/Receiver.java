package transport;
//Candidate Number: 184676

public class Receiver extends NetworkHost {

    /*
    The below constants define the min and max sequence numbers possible. After the max, the number rolls over to the minimum.
    The minimum of 1 is chosen so that 0 can be used to mean no value.
    The maximum of 2 is chosen as it's the minimum that is required to ensure in order sending.
    The choice of values is arbitrary - they could be replaced with any values where MIN <= MAX-1 and the rest of the code would function properly.
    */
    private final static int MINSEQUENCENUMBER = 0;
    private final static int MAXSEQUENCENUMBER = 1;
    
    //Used to ensure packets are arriving in order
    private int expectedSequenceNumber;
    
    //Increments the value of the private field expectedSequenceNumer and if it exceeds the maximum value it rolls over to the minimum
    private void incrementExpectedSequenceNumber() {
        expectedSequenceNumber += 1;
        if (expectedSequenceNumber > MAXSEQUENCENUMBER) {
            expectedSequenceNumber = MINSEQUENCENUMBER;
        }
    }
    
    //Needed to accept resent packets (i.e when the ACK is lost / corrupted) without actually changing the expectedsequence number value
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
    
    // This is the constructor.  Don't touch!
    public Receiver(int entityName) {
        super(entityName);
    }

    //Initialises the values of expectedSequenceNumber
    @Override
    public void init() {
        expectedSequenceNumber = MINSEQUENCENUMBER;
    }

    /**
     * This method checks that the packet isn't corrupted before continuing - if it is, no ACK is sent and the sender resends the packet.
     * If it isn't corrupted, the sequence number is check to find out if it's a new packet or a duplicate of previous packet.
     * If it's a new packet, the data is sent up to the application layer and an ACK packet is sent.
     * If it's a duplicate of the previous packet, the ACK for that packet is resent and the data is ignored.
     * @param packet from the network layer, contains a payload up to 20 bytes which is to be sent to the application layer.
     */
    @Override
    public void input(Packet packet) {
        //Only if the packet isn't corrupted do we check the seqNumber
        if (validateChecksum(packet)) {
            int seqNumber = packet.getSeqnum();
            Packet outputPacket;
            //Accepts next expected packet, sends on the data, and updates next expected value
            if (seqNumber == expectedSequenceNumber) {
                deliverData(packet.getPayload());
                int packetSeqNum = -1;
                int packetAckNum = seqNumber;
                String packetData = "";
                int packetChecksum = packetSeqNum + packetAckNum;
                outputPacket = new Packet(packetSeqNum, packetAckNum, packetChecksum, packetData);
                incrementExpectedSequenceNumber();
                udtSend(outputPacket);
            }

            //Accepts previous packet, used if the last ACK packet was lost or corrupted
            //All that needs to be done in this case is to resend the ACK - the application layer already has the payload.
            else if (seqNumber == getPreviousSequenceNumber()) {
                int packetSeqNum = -1;
                int packetAckNum = seqNumber;
                String packetData = "";
                int packetChecksum = packetSeqNum + packetAckNum;
                outputPacket = new Packet(packetSeqNum, packetAckNum, packetChecksum, packetData);
                udtSend(outputPacket);
            }
        }
    }

}
