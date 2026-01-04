#include <WiFiManager.h>         // https://github.com/tzapu/WiFiManager
#include <ESP8266WiFi.h>
#include <ESPAsyncTCP.h>
#include <WiFiUdp.h>
#include <Ticker.h>


#define PORT 10000  // TCP

#define UDP_PORT 4210
#define UDP_HOST "0.0.0.0"

#define MAX_LENGTH 256
#define TIMER_DELAY 7000 
#define TICKS_MAX 5000
#define PING_TIME 2000
#define RESET_TIME 500

struct RxBuffer {
  uint8_t data[MAX_LENGTH];
  size_t len = 0;
};

RxBuffer rx;
char sendBuffer[65];
const short int GREEN = 3;  // remap RX
const short int YELLOW = 0;
const short int RED = 2;


IPAddress remoteAddress;
bool connectionReady = false;

int state = LOW;

const uint32 clientID = system_get_chip_id();
char clientIDstring[9];

bool running = true;

volatile unsigned int tempo = 100;
char program[MAX_LENGTH];
char rec_program[MAX_LENGTH];
volatile int length;
int rec_length;
volatile int step = 0;

bool DO_MASTER_SYNC = false;
bool offline = false;
bool master = false;

static AsyncClient* client = NULL;
WiFiUDP UDP;
Ticker timer;

volatile int ticks = 0;
volatile int timeSincePing = 0;
volatile bool blinkFlag = false;

// ISR to Fire when Timer is triggered
void ICACHE_RAM_ATTR onTime() {
  //Serial.print(ticks);
  //Serial.print(" ");
  if (ticks++ >= tempo) {
    blinkFlag = true;
    ticks = 0;
    // tempo = newTempo;  // reset tempo only when current interval is complete
  }
  
  timeSincePing++;
/*
  if (timeSincePing % 1000 == 0) {
    Serial.println("\tMASTER timeSincePing = " + (String)timeSincePing);
  }
  if (timeSinceReset % 1000 == 0) {
    Serial.println("\tMASTER timeSinceReset = " + (String)timeSinceReset);
  }
  */
}

/* event callbacks */
static void handleData(void* arg, AsyncClient* client, void *data, size_t len) {
  if (rx.len + len > MAX_LENGTH) {
    // overflow → drop buffer
    Serial.println("handleData overflow");
    rx.len = 0;
    return;
  }

  memcpy(rx.data + rx.len, data, len);
  rx.len += len;

  processRxBuffer();
}

void processRxBuffer() {
  Serial.println("processRxBuffer");
  while (true) {
    // Need at least CMD + LEN
    if (rx.len < 2) return;

    uint8_t cmd = rx.data[0];
    uint8_t payloadLen = rx.data[1];
    size_t packetLen = payloadLen + 2;

    // Wait until full packet is available
    if (rx.len < packetLen) return;

    // Process one packet
    handlePacket(cmd, rx.data + 2, payloadLen);

    // Remove packet from buffer
    memmove(rx.data, rx.data + packetLen, rx.len - packetLen);
    rx.len -= packetLen;
  }
}


void onConnect(void* arg, AsyncClient* client) {
	Serial.print("client has been connected to ");
  Serial.print(remoteAddress);
  Serial.println(" on port " + (String)PORT);
  connectionReady = true;

  // send clientID, to complete setup
  sprintf(sendBuffer, "clientID=%lu", (unsigned long)clientID);
  Serial.println(sendBuffer);
  send(sendBuffer);
}


void setup() {
  Serial.begin(115200);
  while (!Serial && millis() < 5000);
  delay(200);

  WiFi.setSleepMode(WIFI_NONE_SLEEP);
  //GPIO 3 (RX) swap the pin to a GPIO.
  pinMode(RED, FUNCTION_3); 

  pinMode(GREEN, OUTPUT);
  pinMode(YELLOW, OUTPUT);
  pinMode(RED, OUTPUT);

  length = 2;
  tempo = 200;
  program[0] = 0b00000111;
  program[1] = 0b00000000;
  
  timer1_attachInterrupt(onTime); // Add ISR Function
  timer1_enable(TIM_DIV16, TIM_EDGE, TIM_LOOP);  //NB!! Crystal is 26MHz!! Not 80!
  // Arm the Timer for our 0.2s Interval
  timer1_write(TIMER_DELAY); // 25000000 / 5 ticks per us from TIM_DIV16 == 200,000 us interval 

  sprintf(clientIDstring, "%08x", clientID);
  Serial.println("\nTrafikklys 4.1\n");
  Serial.print("ClientID: ");
  Serial.println(clientID);

  //WiFi.begin(ssid, password);             // Connect to the network
  WiFiManager wifiManager;
  wifiManager.setConnectTimeout(4);
  wifiManager.setConnectRetries(4);
  wifiManager.setConfigPortalTimeout(60);
  wifiManager.setAPCallback(configModeCallback);
  wifiManager.setConfigPortalTimeoutCallback(configModeExitCallback);

  // fetches ssid and pass from eeprom and tries to connect
  // if it does not connect it starts an access point with the specified name
  // and goes into a blocking loop awaiting configuration
  wifiManager.autoConnect();
  
  Serial.print("Connected to WiFi network with IP Address: ");
  Serial.println(WiFi.localIP());

  client = new AsyncClient;
  client->onData(&handleData, client);
  client->onConnect(&onConnect, client);

  length = 2;
  tempo = 100;
  program[0] = 0b00000010;
  program[1] = 0b00000000;

  // Begin listening to UDP port
  UDP.begin(UDP_PORT);
  Serial.print("Listening on UDP port ");
  Serial.println(UDP_PORT);

  // wait for an UDP packet, to get the IP address of the server
  while (true) {
    if (readUDP()) {

      Serial.print("remoteAddress: ");
      Serial.println(remoteAddress);
      if (client->connect(remoteAddress, PORT)) {
        Serial.println("CONNECTED");
        break;
      } else {
        Serial.println("Connection failed");
      }
    }
    delay(700);
    Serial.print(".");
  }

  length = 1;
  program[0] = 0b00000100;


  length = 4;
  program[0] = 0b00000001;
  program[1] = 0b00000010;
  program[2] = 0b00000100;
  program[3] = 0b00000010;
}

void send(char data[]) {
	if (client->space() > 32 && client->canSend()) {
		client->add(data, strlen(data));
		client->send();
	} else {
    Serial.println("FAILED to send");
  }
}

char packet[255];
char pingId = '\0';
bool pingUnsync = false;

void configModeCallback (WiFiManager *myWiFiManager) {
  length = 2;
  program[0] = 0b00000001;
  program[1] = 0b00000000;

  Serial.println("Entered config mode");
  Serial.println(WiFi.softAPIP());
  Serial.println(myWiFiManager->getConfigPortalSSID());
}

void configModeExitCallback () {
  ESP.restart();
}

bool readUDP() {
  int packetSize = UDP.parsePacket();
  if (packetSize) {
    Serial.print("Received packet! Size: ");
    Serial.println(packetSize); 
    int len = UDP.read(packet, 255);
    if (len > 0)
    {
      packet[len] = '\0';
    }
    Serial.print("Packet received: ");
    Serial.println(packet);

    remoteAddress = UDP.remoteIP();
    //handlePayload(packet);

    return true;
  }
  return false;
}


void printBytes(char buf[], int len) {;
  Serial.print((String)len + " bytes:\t");
  for (int s = 0; s < len; s++) {
    Serial.print(buf[s]);
  }
  Serial.println("");
}


void loop() {
  if (blinkFlag) {
    blink();
    blinkFlag = false;
  }

  delay(10);
}

void printByte(unsigned char b) {
  for (volatile int i = 0; i < 8; i++) {
    Serial.print((String)((b >> i) & 0b00000001));
  }
}

ICACHE_RAM_ATTR int getBit(unsigned char b, int i) {
  bool inverted = false;
  uint8_t off = (inverted ? HIGH : LOW);
  uint8_t on =  (inverted ? LOW : HIGH);
  return ((b >> i) & 0b00000001) == 1 ? on : off;
}

ICACHE_RAM_ATTR void blink() {
  if (!running) {
    return;
  }
  
  if (++step >= length) {
    step = 0;
  }

  digitalWrite(GREEN, getBit(program[step], 0));
  digitalWrite(YELLOW, getBit(program[step], 1));
  digitalWrite(RED, getBit(program[step], 2));
  
  //Serial.print("\n" + (String)step + ": ");
  //printByte(program[step]);
}



void handlePacket(uint8_t cmd, uint8_t *payload, uint8_t len) {
  uint8_t i;
  uint16_t test;
  
  Serial.print(F("RX cmd=0x"));
  if (cmd < 0x10) Serial.print('0');
  Serial.print(cmd, HEX);

  Serial.print(F(" len="));
  Serial.print(len);

  Serial.print(F(" payload="));
  for (uint8_t i = 0; i < len; i++) {
    Serial.print(F("0x"));
    if (payload[i] < 0x10) Serial.print('0');
    Serial.print(payload[i], HEX);
    Serial.print(' ');
  }
  Serial.println();

  switch (cmd) {

    /*
    case 0x00: // STOP / PAUSE
      running = false;
      break;

    case 0x0A:  // START / RESUME
      running = true;
      break;
      */
      
    case 0x01:  // TEMPO
      test = payload[2];

      // safety....
      if (test < 1) test = 1;
      else if (test > 255) test = 256;
      
      tempo = test;
      if (tempo > ticks) {
        ticks = 0;
      }
      break;
      
    case 0x02:  // RESET (restart nåværende program)
      step = 0;
      running = true;
      break;
  
    case 0x03:  // MOTTA PROGRAM  (dump payload inn i *rec_program)
      //memcpy(rec_program, payload, len);  
      for (i = 0; i < len; i++) {
        rec_program[i] = payload[i + 2];
      }
      rec_length = len;
      break;
    
    case 0x04: // BYTT PROGRAM (bytt om referanser og bruk av *program og *rec_program)
      //swapArrays(program, rec_program);
      for (i = 0; i < rec_length; i++) {
        program[i] = rec_program[i];
      }
      length = rec_length;
      step = 0;  // og RESET!
      running = true;
      break;

      
    default:
      break;
  }
}
