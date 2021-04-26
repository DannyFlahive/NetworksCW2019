package transport;
//Candidate Number: 184676

public class Sender extends NetworkHost {    
    //Values that won't change during execution, so declared as constant.
    //-1 is used to mean null value as 0 is the min.
    private final static int MINSEQUENCENUMBER = 0;
    private final static int MAXSEQUENCENUMBER = 49;
    private final static int EXPECTEDRTT = 40;
    private final static int WINDOWSIZE = 8;
    
    //Only need to keep track of the highest ACK as it is cumulative.
    private int highestAck;
    
    //Use an array to represent the buffer, use it circularly.
    private Packet[] packetBuffer;
    //The index of the first packet in the window.
    private int baseSequenceNumber;
    //The sequence number given to the next packet that's created.
    private int nextSequenceNumber;
    
    /**
     * Takes a given sequence number and increments it, rolling over from MAX sequence number to MIN.
     * @param num the sequence number to be incremented.
     * @return the next sequence number.
     */
    private int incrementGivenSequenceNumber(int num) {
        num += 1;
        if (num > MAXSEQUENCENUMBER) {
            num = MINSEQUENCENUMBER;
        }
        return num;
    }
    
    /**
     * Increments the class variable nextSequenceNumber, used after a packet is created, ensures two packets don't have the same number.
     */
    private void incrementNextSequenceNumber() {
        nextSequenceNumber += 1;
        if (nextSequenceNumber > MAXSEQUENCENUMBER) {
            nextSequenceNumber = MINSEQUENCENUMBER;
        }
    }
    
    /**
     * Used to calculate the sequence number of the most recent packet added to the buffer.
     * This is helpful in timerInterrupt when working out how many packets need to be resent.
     * Values appropriately roll back from MIN sequence number to MAX.
     * @return The value of the sequence number before nextSequenceNumber.
     */
    private int getPreviousSequenceNumber() {
        int previousSequenceNumber = nextSequenceNumber - 1;
        if (previousSequenceNumber < MINSEQUENCENUMBER) {
            previousSequenceNumber = MAXSEQUENCENUMBER;
        }
        return previousSequenceNumber;
    }
    
    /**
     * Using the class variable baseSequenceNumber, work out what the sequence number of the final packet in the window is.
     * Ensures that the value is incremented the correct number of times and appropriately rolls over from MAX sequence number to MIN.
     * @return The sequence number of the end of the window.
     */
    private int getEndOfWindowSequenceNumber(){
        int outputNumber = baseSequenceNumber;
        for (int i = 1; i <  WINDOWSIZE; i++) {
            outputNumber += 1;
            if (outputNumber > MAXSEQUENCENUMBER) {
                outputNumber = MINSEQUENCENUMBER;
            }
        }
        return outputNumber;
    }
    
    /**
     * Checks whether a given sequence number is within the packet window, accounting for extreme cases around the MAX and MIN values.
     * Used in various places, including the timerInterrupt and output.
     * @param seqNum The sequence number to be checked.
     * @return True if the sequence number is in the packet window, false otherwise
     */
    private boolean validateInPacketWindow(int seqNum) {
        //Case 1: general case, sequence number between start and end of window.
        //Case 2: Start of window close to MAX and end of window is close to MIN, due to wrap around.
        //In this case, anything above start or below end of window would be within the window, so both these options are accounted for.
        return (seqNum >= baseSequenceNumber && seqNum <= getEndOfWindowSequenceNumber()                                                                //eg base = 10, end = 18, current = 15
                || (seqNum >= baseSequenceNumber && seqNum > getEndOfWindowSequenceNumber() && getEndOfWindowSequenceNumber() < baseSequenceNumber)     //eg base = 45, end = 2, current = 48
                || (seqNum < baseSequenceNumber && seqNum <= getEndOfWindowSequenceNumber() && getEndOfWindowSequenceNumber() < baseSequenceNumber));   //eg base = 45, end = 2, current = 1
    }
    
    /**
     * Calculated the checksum of a given string by summing the ASCII values of each character.
     * @param string The string used in the checksum.
     * @return The integer sum.
     */
    private int checksumOfString (String string) {
        int runningSum = 0;
        for (char ch : string.toCharArray()) {
            runningSum += (int) ch;
        }
        return runningSum;
    }
    
    /**
     * Ensures the checksum given by a packet is correct by recalculating it.
     * @param packet The packet to be checked.
     * @return True if the checksum is correct, false otherwise.
     */
    private boolean validateChecksum (Packet packet) {
        int calculatedChecksum = packet.getSeqnum() + packet.getAcknum() + checksumOfString(packet.getPayload());
        return calculatedChecksum == packet.getChecksum();
    }
    
    //Constructor.
    public Sender(int entityName) {
        super(entityName);
    }
    
    //Sets up the non-constant class variables.
    @Override
    public void init() {
        packetBuffer = new Packet[50];
        highestAck = -1;
        baseSequenceNumber = MINSEQUENCENUMBER;
        nextSequenceNumber = baseSequenceNumber;
    }
    
    /**
     * Called by the application layer, used to send data across the network.
     * Creates a packet for the message, with the appropriate data, sequence number, and checksum.
     * If the packet is within the packet window, it is buffered and immediately sent, 
     * If it's outside the packet window it is just buffered for sending later.
     * If there is a buffer overflow when trying to add the packet, the application quits.
     * @param message The message to be sent across the network.
     */
    @Override
    public void output(Message message) {        
        //Generate Packet.
        String data = message.getData();
        int ackNum = -1;
        int checksum = nextSequenceNumber + ackNum + checksumOfString(data);
        Packet outputPacket = new Packet(nextSequenceNumber, ackNum, checksum, data);
        
        //Add to buffer.
        packetBuffer[nextSequenceNumber] = outputPacket;
        
        //Check whether it needs to be sent now.
        //If this packet is the base packet, start the timer.
        if (validateInPacketWindow(nextSequenceNumber)) {
            udtSend(outputPacket);
            if (nextSequenceNumber == baseSequenceNumber) {
                startTimer(EXPECTEDRTT);
            }
        }
        
        
        incrementNextSequenceNumber();
        
        //Checks if an overflow occured, happens when nextSequenceNumber loops all the way back around to equal baseSequenceNumber.
        if(nextSequenceNumber == baseSequenceNumber) {
            //Quit the appliaction.
            System.out.println("Buffer capacity exceeded!");
            System.exit(0);
        }
        
    }
    
    
    /**
     * This method will be called whenever a packet sent from the receiver (i.e. as a result of a udtSend()) arrives at the sender.  
     * @param packet The packet sent from the receiver, possibly corrupted.
     */
    @Override
    public void input(Packet packet) {
        //Check the packet is valid.
        //Get ACK number - if in window, update current highestACK.
        //Move the window along to the packet after the highestACK 
        if (validateChecksum(packet)) {
            int ackNum = packet.getAcknum();
            if (validateInPacketWindow(ackNum)) {
                highestAck = ackNum;
                baseSequenceNumber = incrementGivenSequenceNumber(highestAck);
                stopTimer();
                if (baseSequenceNumber != nextSequenceNumber) {
                    startTimer(EXPECTEDRTT);
                }
            }
        } 
    }
    
    /**
     * Called by the timer when it expires. Used to resend all the necessary packets.
     */
    @Override
    public void timerInterrupt() {
        //End of window needs to be the the next NOT NEEDED sequence number due to the way the while loop works.
        //So the sequence number after end of window, or the next sequence number.
        int endOfWindow;
        if (validateInPacketWindow(getPreviousSequenceNumber())) {
            //If the window isn't full, only send the packets that are in it.
            endOfWindow = nextSequenceNumber;
            
        }
        else {
            //Otherwise, the window is full so we can send the full window again.
            endOfWindow = incrementGivenSequenceNumber(getEndOfWindowSequenceNumber());
        }
        //Until at the end of window, send packets, then start the timer again.
        int currentSequenceNumber = baseSequenceNumber;
        while (currentSequenceNumber != endOfWindow) {
            udtSend(packetBuffer[currentSequenceNumber]);
            currentSequenceNumber = incrementGivenSequenceNumber(currentSequenceNumber);
        }
        startTimer(EXPECTEDRTT);
    }
}
