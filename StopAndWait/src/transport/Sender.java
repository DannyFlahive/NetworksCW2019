package transport;
//Candidate Number: 184676

public class Sender extends NetworkHost {    
    /*
    The below constants define the min and max sequence numbers possible. After the max, the number rolls over to the minimum.
    The minimum of 1 is chosen so that 0 can be used to mean no value.
    The maximum of 2 is chosen as it's the minimum that is required to ensure in order sending.
    The choice of values is arbitrary - they could be replaced with any values where MIN <= MAX-1 and the rest of the code would function properly.
    */
    private final static int MINSEQUENCENUMBER = 0;
    private final static int MAXSEQUENCENUMBER = 1;
    
    //The expected round trip time (RTT), used to decide after how long packets should be resent.
    private final static int EXPECTEDRTT = 40;
    
    /*
    The below variable are used to maintain state information - whether the network is busy sending a packet,
    what the current packet actually is (in case it's dropped / corrupted), and what the current packet's sequence number is.
    They are all useful for various reasons throughout the rest of the class.
    */
    private int currentSequenceNumber;
    private boolean currentlySending;
    private Packet currentPacket;
    
    //Increments the value of currentSequenceNumer, and if it exceeds the maximum value it rolls over to the mimimum value.
    private void incrementSequenceNumber() {
        currentSequenceNumber += 1;
        if (currentSequenceNumber > MAXSEQUENCENUMBER) {
            currentSequenceNumber = MINSEQUENCENUMBER;
        }
    }
    
    //Gets the checksum for a string by summing the ASCII value of every character.
    private int checksumOfString (String string) {
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
    private boolean validateChecksum (Packet packet) {
        int calculatedChecksum = packet.getSeqnum() + packet.getAcknum() + checksumOfString(packet.getPayload());
        return calculatedChecksum == packet.getChecksum();
    }
    
    // This is the constructor.  Don't touch!
    public Sender(int entityName) {
        super(entityName);
    }

    //Initialises the values of currentSequenceNumber and currentlySending.
    @Override
    public void init() {
        currentSequenceNumber = MINSEQUENCENUMBER;
        currentlySending = false;
    }
    
    /**
     * This method wraps the message up into a packet with appropriate seqnum, acknum, checksum, and payload (message). 
     * It includes features that ensure in-order delivery (using sequence number but not updating it - this only happens once an ACK is received).
     * It includes features that account for packet loss and corruption (storing the packet, setting the timer to resend).
     * It checks that the network is available before doing any of this - if a message is currently being sent, this message is dropped.
     * @param message from the application layer, up to 20 bytes, to be sent over the network.
     */
    @Override
    public void output(Message message) {
        if(!currentlySending) {
            currentlySending = true;
            
            //Sequence number is taken from the Senders current sequence number, ACK number is set to 0 (indicating that this isn't an ACK message),
            //the payload is taken from the message (input to this method), and the checksum is calculated using all these values.
            String data = message.getData();
            int ackNum = -1;
            int checksum = currentSequenceNumber + ackNum + checksumOfString(data);
            
            //The above values are put together in a packet, saved in case of packet loss/corruption, and sent to the network layer.
            Packet outputPacket = new Packet(currentSequenceNumber, ackNum, checksum, data);
            currentPacket = outputPacket;
            udtSend(outputPacket);
            //After the timer expires, the message is assumed to be lost, so can be resent.
            startTimer(EXPECTEDRTT);
        }
        //By design, when currently sending the 'if' doesn't trigger, so no code is executed, essentially dropping the data
    }
    
    
    /**
     * When a packet is received, it's checked for corruption using the private validation method.
     * If it's not corrupted and has the correct ACK number (the sequence number of the last packet sent), it is accepted.
     * The method then updates some class variables and stops the timer, so that the sender is ready to send the next packet.
     * The method ignores duplicate ACKS by only operating if the ACK number matches that of the last sent packet.
     * @param packet The incoming ACK packet.
     */
    @Override
    public void input(Packet packet) {
        if (packet.getAcknum() == currentSequenceNumber && validateChecksum(packet)) {
            currentlySending = false;
            incrementSequenceNumber();
            stopTimer();
        }
    }
    
    
    /**
     * Called whenever a timer expires. This indicates that the last packet was lost or corrupted.
     * This method therefore retransmits the last packet and starts the timer again.
     */
    @Override
    public void timerInterrupt() {
        udtSend(currentPacket);
        startTimer(EXPECTEDRTT);
    }
}
