import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import jdk.dio.DeviceManager;
import jdk.dio.i2cbus.I2CDevice;
import jdk.dio.i2cbus.I2CDeviceConfig;

/**
 *
 * @author Leonid Burdikov loebrud@icloud.com
 */
public class SensorHTS221 {
    // Configuration.
    // Contains information about sensor, its address on i2c bus
    // and clock rate.
    private final I2CDeviceConfig conf;
    
    // Device.
    // Represents the device.
    private I2CDevice dev;
    
    // Calibration values
    private float H0_rH;
    private float H1_rH;
    private float T0_degC;
    private float T1_degC;
    private short H0_T0_OUT;
    private short H1_T0_OUT;
    private short T0_OUT;
    private short T1_OUT;
    
    // whether the device is powered on
    private boolean powered;
    // whether the device ODR set to oneshot
    private boolean oneshot;

   /** Constructs new instance of this class.
    * 
    * @param controllerNumber Number of I2C Bus controller (usually 1).
    * @param clockFrequency Either 100000 or 400000 Hz.
    */
    SensorHTS221(int controllerNumber, int clockFrequency) throws IOException {
        conf = new I2CDeviceConfig.Builder()
                        .setControllerNumber(controllerNumber) 
                        .setAddress(0xBE / 2, I2CDeviceConfig.ADDR_SIZE_7)
                        .setClockFrequency(clockFrequency)
                        .build();
        oneshot = true;
        powered = false;
        getCalibrationValues();
    }
    
   /** Constructs new instance of this class.
    * <p>Controller number is set to 1. Clock frequency - 100000 Hz.
    */
    SensorHTS221() throws IOException{
        this(1,100000);
    }
        
   /** Returns the content of WHO_AM_I register.
    * <p>It must contain value -68 (0xBE). If it's not, use {@link reboot()}.
    * @return value of WHO_AM_I register.
    * @throws IOException 
    */
    public byte whoAmI() throws IOException{
        dev = DeviceManager.open(conf);
        
        ByteBuffer buf = ByteBuffer.allocateDirect(1);
        
        dev.read(0x0F, 1, buf); // 0x0F - WHO_AM_I register
        dev.close();

        buf.rewind();
        return buf.get();
    }
    
   /** Humidity and temperature resolution mode.
    * <p>Configure the number of temperature and humidity samples.
    * <table>
    *   <tr><th>Value</th><th>Temperature</th><th>Humidity</th></tr>
    *   <tr><td>0</td><td>2</td><td>4</td></tr>
    *   <tr><td>1</td><td>4</td><td>8</td></tr>
    *   <tr><td>2</td><td>8</td><td>16</td></tr>
    *   <tr><td>3</td><td>16</td><td>32</td></tr>
    *   <tr><td>4</td><td>32</td><td>64</td></tr>
    *   <tr><td>5</td><td>64</td><td>128</td></tr>
    *   <tr><td>6</td><td>128</td><td>256</td></tr>
    *   <tr><td>7</td><td>256</td><td>512</td></tr>
    * </table>
    * <p>For additional information on noize estimation and energy consumption
    * refer to the datasheet.
    * @param rateTemp Value in 0-7.
    * @param rateHum Value in 0-7.
    * @throws IOException
    * @throws IllegalArgumentException 
    */ 
    public void setAVG(int rateTemp, int rateHum) throws IOException, IllegalArgumentException{
        if (rateTemp < 0 | rateTemp > 7) throw new IllegalArgumentException("rateTemp should be in range 0-7");
        if (rateHum < 0 | rateHum > 7) throw new IllegalArgumentException("rateHum should be in range 0-7");
        
        ByteBuffer buf = ByteBuffer.allocateDirect(1);
        
        dev = DeviceManager.open(conf);
        dev.read(0x10,1,buf);
        byte reg = buf.get(0);
        reg = (byte) (reg & 0b1100_0000 | (rateTemp << 3) | rateHum);
        buf.put(0, reg);
        buf.rewind();
        dev.write(0x10,1,buf);
        dev.close();
    }
    
   /** Sets temperature resolution mode.
    * <p>See {@link setAVG(int rateTemp, int rateHum)}.
    * @param rateTemp Value in 0-7.
    * @throws IOException 
    */ 
    public void setAVGT(int rateTemp) throws IOException{
        if (rateTemp < 0 | rateTemp > 7) throw new IllegalArgumentException("rateTemp should be in range 0-7");
        
        ByteBuffer buf = ByteBuffer.allocateDirect(1);
        
        dev = DeviceManager.open(conf);
        dev.read(0x10,1,buf);
        byte reg = buf.get(0);
        reg = (byte) (reg & 0b1100_0111 | (rateTemp << 3));
        buf.put(0,reg);
        buf.rewind();
        dev.write(0x10,1,buf);
        dev.close();
    }
   
   /** Sets humidity resolution mode.
    * <p>See {@link setAVG(int rateTemp, int rateHum)}.
    * @param rateHum Value in 0-7.
    * @throws IOException 
    */ 
    public void setAVGH(int rateHum) throws IOException{
        if (rateHum < 0 | rateHum > 7) throw new IllegalArgumentException("rateHum should be in range 0-7");
        
        ByteBuffer buf = ByteBuffer.allocateDirect(1);
        
        dev = DeviceManager.open(conf);
        dev.read(0x10,1,buf);
        byte reg = buf.get(0);
        reg = (byte) (reg & 0b1111_1000 | rateHum);
        buf.put(0,reg);
        buf.rewind();
        dev.write(0x10,1,buf);
        dev.close();
    }
    
   /** Sets the PD bit to desired value.
    * 
    * <p>The PD bit is used to turn on the device. The device is in power-down
    * mode when PD = ‘0’ (default value after boot). 
    * The device is active when PD is set to ‘1’.
    * 
    * <p>Device must be turned on in order to get samples.
    */
    public void setPower(boolean enable) throws IOException{
        dev = DeviceManager.open(conf);                 // get channel open
        ByteBuffer buf = ByteBuffer.allocateDirect(1);  // prepare buffer
        dev.read(0x20,1,buf);                           // read the value of the register to the buffer
        byte reg = buf.get(0);                          // get it from buffer
        if (enable) reg |= 0b1000_0000; else reg &= 0b0111_1111; // change it
        buf.put(0, reg);                                // put it back to buffer
        buf.rewind();
        dev.write(0x20,1,buf);                          // write it to the register
        dev.close();                                    // close the channel
        powered = enable;                               // remember the state
    }
    
   /** Sets the BDU bit to desired value.
    * 
    * <p>The BDU bit is used to inhibit the output register update between the reading of the upper
    * and lower register parts. In default mode (BDU = ‘0’), the lower and upper register parts are
    * updated continuously. If it is not certain whether the read will be faster than output data rate,
    * it is recommended to set the BDU bit to ‘1’. In this way, after the reading of the lower (upper)
    * register part, the content of that output register is not updated until the upper (lower) part is
    * read also.
    * 
    * <p>This feature prevents the reading of LSB and MSB related to different samples.
    */
    public void setBDU(boolean enable) throws IOException{
        dev = DeviceManager.open(conf);                 // get channel open
        ByteBuffer buf = ByteBuffer.allocateDirect(1);  // prepare buffer
        dev.read(0x20,1,buf);                           // read the value of the register to the buffer
        byte reg = buf.get(0);                          // get it from buffer
        if (enable) reg |= 0b0000_0100; else reg &= 0b1111_1011; // change it
        buf.put(0, reg);                                // put it back to buffer
        buf.rewind();
        dev.write(0x20,1,buf);                          // write it to the register
        dev.close();                                    // close the channel
    }
   
   /** Sets ODR bits to desired value.
    * 
    * <p>The ODR1 and ODR0 bits permit changes to the output data rates of humidity and
    * temperature samples.The default value corresponds to “one shot” configuration for both
    * humidity and temperature output.
    * 
    * <p> 0 - both rates are set to "One shot"
    * <p> 1 - both rates are set to 1 Hz
    * <p> 2 - both rates are set to 7 Hz
    * <p> 3 - both rates are set to 12.5 Hz
    * 
    * @param rate integer value in range between 0 and 3.
    */
    public void setODR(int rate) throws IllegalArgumentException, IOException{
        if (rate < 0 || rate > 3) throw new IllegalArgumentException("Argument "
                + "should be in range between 0 and 3.");
        
        dev = DeviceManager.open(conf);
        
        ByteBuffer buf = ByteBuffer.allocateDirect(1);
        
        dev.read(0x20,1,buf); // 0x20 - CTRL_REG1
        byte reg = buf.get(0);
        reg &= 0b1111_1100; //  Zero and first bits - ODR bits. Cleared.
        reg |= rate; // Set to desired value.
        buf.put(0, reg);
        buf.rewind();
        dev.write(0x20,1,buf);
        
        oneshot = rate == 0;
        
        dev.close();
    }
    
   /** Sets the BOOT bit to 1.
    * 
    * <p>The BOOT bit is used to refresh the content of the internal registers stored in the Flash
    * memory block. At device power-up, the content of the Flash memory block is transferred to
    * the internal registers related to trimming functions to permit good behavior of the device
    * itself. If, for any reason, the content of the trimming registers is modified, it is sufficient to
    * use this bit to restore the correct values. When the BOOT bit is set to ‘1’ the content of the
    * internal Flash is copied inside the corresponding internal registers and is used to calibrate
    * the device. These values are factory trimmed and are different for every device. They permit
    * good behavior of the device and normally they should not be changed. At the end of the
    * boot process, the BOOT bit is set again to ‘0’.
    */
    public void reboot() throws IOException, InterruptedException {
        dev = DeviceManager.open(conf);
        
        ByteBuffer buf = ByteBuffer.allocateDirect(1);
        
        dev.read(0x21,1,buf);
        
        byte reg = buf.get(0);
        reg |= 0b1000_0000;
        buf.put(0,reg);
        buf.rewind();
        
        dev.write(0x21,1,buf);
        
        dev.close();
        Thread.sleep(100);
        getCalibrationValues();
    }

   /**Sets the Heater bit to the desired value.
    * 
    * <p>The Heater bit is used to control an internal heating element, that can effectively be used to
    * speed up the sensor recovery time in case of condensation. The heater can be operated
    * only by an external controller, which means that it has to be switched on/off directly by FW.
    * Humidity and temperature output should not be read during the heating cycle; valid data can
    * be read out once the heater has been turned off, after the completion of the heating
    * cycle.
    * @param enable
    * @throws IOException 
    */
    public void setHeater(boolean enable) throws IOException{
        dev = DeviceManager.open(conf);                 // get channel open
        ByteBuffer buf = ByteBuffer.allocateDirect(1);  // prepare buffer
        dev.read(0x21,1,buf);                           // read the value of the register to the buffer
        byte reg = buf.get(0);                          // get it from buffer
        if (enable) reg |= 0b0000_0010; else reg &= 0b1111_1101; // change it
        buf.put(0,reg);                                 // put it back to buffer
        buf.rewind();
        dev.write(0x21,1,buf);                          // write it to the register
        dev.close();                                    // close the channel
    }
    
    /**Initiates "One shot" procedure.
     *
     * <p>During that procedure a single aquisition of samples is done. Data
     * is written to its corresponding registers and status register is updated.
     * <p>Use this to get data when the ODR is set to "One shot" and only if
     * you are using boolean-returning get method. You don't need to initiate
     * one shot manually when you are using value-returnig get method.
     * @throws IOException
     * @throws InterruptedException
     */
    public void oneShot() throws IOException, InterruptedException{
        dev = DeviceManager.open(conf);
        ByteBuffer buf = ByteBuffer.allocateDirect(1);
        dev.read(0x21,1,buf);
        byte reg = buf.get(0);
        reg |= 0b0000_0001;
        buf.put(0, reg);
        buf.rewind();
        dev.write(0x21,1,buf);
        dev.close();
        Thread.sleep(50);
    }
    
    /**Set the selection mode on pin 3.
     *
     * <p>It can be either Push-Pull or Open Drain. Push-pull is set by default.
     * @param enable True - Push-Pull. False - Open Drain.
     * @throws IOException
     */
    public void setPushPull(boolean enable) throws IOException{
        dev = DeviceManager.open(conf);                 // get channel open
        ByteBuffer buf = ByteBuffer.allocateDirect(1);  // prepare buffer
        dev.read(0x22,1,buf);                           // read the value of the register to the buffer
        byte reg = buf.get(0);                          // get it from buffer
        if (enable) reg |= 0b0100_0000; else reg &= 0b1011_1111; // change it
        buf.put(0, reg);                                // put it back to buffer
        dev.write(0x22,1,buf);                          // write it to the register
        dev.close();                                    // close the channel
    }
    
    /**Data ready output signal.
     *
     * <p>Either high or low.
     * @param high
     * @throws IOException
     */
    public void setDrDyOutput(boolean high) throws IOException{
        dev = DeviceManager.open(conf);                 // get channel open
        ByteBuffer buf = ByteBuffer.allocateDirect(1);  // prepare buffer
        dev.read(0x22,1,buf);                           // read the value of the register to the buffer
        byte reg = buf.get(0);                          // get it from buffer
        if (high) reg &= 0b0111_1111; else reg |= 0b1000_0000; // change it
        buf.put(0, reg);                                // put it back to buffer
        dev.write(0x22,1,buf);                          // write it to the register
        dev.close();                                    // close the channel
    }
    
    private void getCalibrationValues() throws IOException{
        ByteBuffer buf = ByteBuffer.allocateDirect(16);
        
        dev = DeviceManager.open(conf);
        dev.read(0xB0,1,buf);
        dev.close();
        
        buf.rewind();
        
        byte buf1 = buf.get(); // H0_rH_x2 value
        H0_rH = ((float)(buf1 >= 0 ? buf1 : 256 - buf1)) / 2;
        
        byte buf2 = buf.get(); // H1_rH_x2 value
        H1_rH = ((float)(buf2 >= 0 ? buf2 : 256 - buf2)) / 2;

        buf1 = buf.get(); // LSB of T0_degC_x8
        buf2 = buf.get(); // LSB of T1_degC_x8
        
        buf.get(); // skip reserved
        
        byte buf3 = buf.get(); // this contains MSB for both T0 and T1 degC_x8
        byte buf4 = buf3;
         
        buf3 &= 0b0000_0011; // a little bit of dancing with buben
        short buf3s = buf3;
        buf3s <<= 8;
        T0_degC = ((float)(buf3s | buf1)) / 8;
        
        buf4 &= 0b0000_1100;
        short buf4s = buf4;
        buf4s <<= 6;
        T1_degC = ((float)(buf4s | buf2)) / 8;
        
        buf.order(ByteOrder.LITTLE_ENDIAN);
        H0_T0_OUT = buf.getShort();
        buf.getShort(); // skip reserved
        H1_T0_OUT = buf.getShort();
        T0_OUT = buf.getShort();
        T1_OUT = buf.getShort();
    }
    
    @Deprecated
    /**Gets the temperature value from corresponding register.
     *
     * <p>Automatically initiates one shot if ODR is set to one shot. Checks 
     * status register until it shows that new data is available. Can be unsafe 
     * because it contains an infinite loop. Power bit should be set to 1, 
     * if not, the returning value is -274.
     * @return Temperature in degrees of Celcius.
     * @throws IOException
     * @throws InterruptedException
     */
    public float getTemperature() throws IOException, InterruptedException{
        if (!powered) return -274; // turn device on first!
        if (oneshot) oneShot();
        
        ByteBuffer buf1 = ByteBuffer.allocateDirect(1);
        byte reg;
        
        while (true){
            dev = DeviceManager.open(conf);
            dev.read(0x27,1,buf1); // read status register until there is new data
            dev.close();
            buf1.rewind();
            reg = buf1.get();
            if ((reg & 0b0000_0001) == 1) break;
            buf1.rewind();
        }
        
        
        ByteBuffer buf = ByteBuffer.allocateDirect(2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        
        dev = DeviceManager.open(conf);
        dev.read(0xAA,1,buf);
        dev.close();
        
        return (buf.getShort(0) - T0_OUT) * (T1_degC - T0_degC) / (T1_OUT - T0_OUT) + T0_degC;
    }
    
    @Deprecated
    /**Gets the relative humidity value from corresponding register.
     *
     * <p>Automatically initiates one shot if ODR is set to one shot. Checks status register 
     * until it shows that new data is available. Can be unsafe because it 
     * contains infinite loop. Power bit should be set to 1, if not, the returning
     * value is -1.
     * @return Relative humidity value in percents.
     * @throws IOException
     * @throws InterruptedException
     */
    public float getHumidity() throws IOException, InterruptedException{
        if (!powered) return -1;
        if (oneshot) oneShot();
        
        ByteBuffer buf1 = ByteBuffer.allocateDirect(1);
        byte reg;
        
        while (true){
            dev = DeviceManager.open(conf);
            dev.read(0x27,1,buf1); // read status register until there is new data
            dev.close();
            buf1.rewind();
            reg = buf1.get();
            if ((reg & 0b0000_0010) == 2) break;
            buf1.rewind();
        }

        ByteBuffer buf = ByteBuffer.allocateDirect(2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        
        dev = DeviceManager.open(conf);
        dev.read(0xA8,1, buf);
        dev.close();
        
        return (buf.getShort(0) - H0_T0_OUT) * (H1_rH - H0_rH) / (H1_T0_OUT - H0_T0_OUT) + H0_rH;
    }
    
    /**Contains set of variables used to pass value back from get methods.
     * 
     */
    public static class Args {
        Args(){
            err = 0;
            msg = "";
            Temperature = -274;
            Humidity = -1;
        }

        /**Error code.
         * <p>0 - no errors.
         * <p>1 - Power bit is not set to 1.
         * <p>2 - There is no new data available.
         * <p>3 - Less than 2 bytes was read from register.
         */
        public int err;

        /**Error message.
         *
         */
        public String msg;

        /**Temperature value.
         *
         */
        public float Temperature;

        /**Relative humidity value.
         *
         */
        public float Humidity;
    }
    
    /**Gets the temperature value from corresponding register.
     *
     * Doesn't initiate one shot, so you have to do it. Checks status register
     * only once, if there is new data, it puts it in Temperature variable of args.
     * Returns true if it successfully gets the value, false in other cases.
     * Check the err and msg variables of args in that case.
     * @param args See {@link Args}.
     * @return True, if try was successful.
     * @throws IOException
     */
    public boolean getTemperature(Args args) throws IOException{
        if (!powered) {
            args.err = 1;
            args.msg = "Power bit is not set to 1.";
            return false;
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(1);
        dev = DeviceManager.open(conf);
        dev.read(0x27,1,buf);
        if ((buf.get(0) & 0b0000_0001) != 1) {
            args.err = 2;
            args.msg = "There is no new data available.";
            dev.close();
            return false;
        }
        buf = ByteBuffer.allocateDirect(2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int read = dev.read(0xAA,1,buf);
        dev.close();
        if (read < 2) {
            args.err = 3;
            args.msg = "Less than 2 bytes was read.";
            return false;
        }
        args.Temperature = (buf.getShort(0) - T0_OUT) * (T1_degC - T0_degC) / (T1_OUT - T0_OUT) + T0_degC;
        args.err = 0;
        args.msg = "There is no error message. You don't see it.";
        return true;
    }
    
    /**Gets the relative humidity value from corresponding register.
     *
     * Doesn't initiate one shot, so you have to do it if ODR is set to one shot. 
     * Checks status register only once, if there is new data, it puts it in 
     * Humidity variable of args. Returns true if it successfully gets the value, 
     * false in other cases. Check the err and msg variables of args in that case.
     * @param args See {@link Args}.
     * @return True, if try was successful.
     * @throws IOException
     */
    public boolean getHumidity(Args args) throws IOException{
        if (!powered) {
            args.err = 1;
            args.msg = "Power bit is not set to 1.";
            return false;
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(1);
        dev = DeviceManager.open(conf);
        dev.read(0x27,1,buf);
        if ((buf.get(0) & 0b0000_0010) != 2) {
            args.err = 2;
            args.msg = "There is no new data available.";
            dev.close();
            return false;
        }
        buf = ByteBuffer.allocateDirect(2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int read = dev.read(0xA8,1,buf);
        dev.close();
        if (read < 2) {
            args.err = 3;
            args.msg = "Less than 2 bytes was read.";
            return false;
        }
        args.Humidity = (buf.getShort(0) - H0_T0_OUT) * (H1_rH - H0_rH) / (H1_T0_OUT - H0_T0_OUT) + H0_rH;
        args.err = 0;
        args.msg = "There is no error message. You don't see it.";
        return true;
    }
}
