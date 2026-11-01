#include <WiFiManager.h>         // https://github.com/tzapu/WiFiManager
#include <ESP8266WiFi.h>
#include <WiFiUdp.h>
#include <Ticker.h>

#define UDP_PORT 4210
#define UDP_HOST "0.0.0.0"

#define MAX_LENGTH 256
#define TIMER_DELAY 7000 
#define TICKS_MAX 5000
#define PING_TIME 2000
#define RESET_TIME 500

#define SYNC_JUMP_TICKS 29   // beacon errors above this (~40 ms) jump straight to the target
#define SYNC_DEBUG 1         // print one line per sync beacon on the serial port (TX only)

#define CMD_ANNOUNCE 0x77
#define CMD_HEARTBEAT 0x66

uint8_t udpRxBuf[MAX_LENGTH];
size_t udpRxLen = 0;

char sendBuffer[65];
const short int GREEN = 3;  // remap RX
const short int YELLOW = 0;
const short int RED = 2;

IPAddress remoteAddress;
bool remoteKnown = false;
bool ready = false;
bool programSet = false;  // app has swapped in a program since boot
int state = LOW;

const uint32 clientID = system_get_chip_id();
char clientIDstring[9];

volatile bool hardSync = true;  // next sync beacon sets the position outright
volatile unsigned int tempo = 100;
char program[MAX_LENGTH];
char rec_program[MAX_LENGTH];
volatile int length;
int rec_length;
volatile int step = 0;

bool offline = false;

WiFiUDP UDP;
Ticker timer;

volatile int ticks = 0;

// ISR to Fire when Timer is triggered: drives the lamps directly, so the loop() can't add jitter
void ICACHE_RAM_ATTR onTime() {
  ticks++;
  if (ticks >= tempo) {
    ticks = 0;
    blink();
  }
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
  timer1_write(TIMER_DELAY); // 25000000 / 5 ticks per  us from TIM_DIV16 == 200,000 us interval 

  sprintf(clientIDstring, "%08x", clientID);
  Serial.println("\nTrafikklys 4.2\n");
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

  // length = 2;
  // tempo = 100;
  // program[0] = 0b00000010;
  // program[1] = 0b00000000;

  // Begin listening to UDP port
  UDP.begin(UDP_PORT);
  Serial.print("Listening on UDP port ");
  Serial.println(UDP_PORT);
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

void pollUdp() {
  int packetSize = UDP.parsePacket();
  if (packetSize <= 0) return;

  if (packetSize > MAX_LENGTH) {
    // Oversized packet → discard
    while (UDP.available()) UDP.read();
    return;
  }

  udpRxLen = UDP.read(udpRxBuf, packetSize);
  // Always reply to whoever last talked to us (phone may have changed)
  remoteAddress = UDP.remoteIP();
  if (!remoteKnown) {
    remoteKnown = true;

    length = 2;
    tempo = 100;
    program[0] = 0b00000100;
    program[1] = 0b00000000;
  }

  if (udpRxLen != packetSize) {
    // Should not happen, but be safe
    udpRxLen = 0;
    return;
  }

  processUdpPacket(udpRxBuf, udpRxLen);
}

void sendAnnounce() {
  uint8_t packet[7];
  packet[0] = CMD_ANNOUNCE;
  packet[1] = 5;
  pack_u32(clientID, packet + 2);
  packet[6] = programSet ? 1 : 0;
  sendUdp(packet, sizeof(packet));
}

inline void pack_u32(uint32_t value, uint8_t *out) {
  out[0] = (value >> 24) & 0xFF;
  out[1] = (value >> 16) & 0xFF;
  out[2] = (value >> 8)  & 0xFF;
  out[3] = value & 0xFF;
}

inline void pack_u16(uint16_t value, uint8_t *out) {
  out[0] = (value >> 8) & 0xFF;
  out[1] = value & 0xFF;
}

inline void pack_u8(uint8_t value, uint8_t *out) {
  out[0] = value;
}


bool sendUdp(const uint8_t *data, size_t len) {
  if (!remoteKnown || len == 0) return false;

  if (!UDP.beginPacket(remoteAddress, UDP_PORT)) {
    return false;
  }

  UDP.write(data, len);
  return UDP.endPacket();
}
bool sendUdp(const char *text) {
  if (text == nullptr) return false;
  return sendUdp((const uint8_t *)text, strlen(text));
}


void printBytes(char buf[], int len) {;
  Serial.print((String)len + " bytes:\t");
  for (int s = 0; s < len; s++) {
    Serial.print(buf[s]);
  }
  Serial.println("");
}


void loop() {
  static unsigned long lastHeartbeat = 0;

  if (remoteKnown && millis() - lastHeartbeat > 3000) {
    lastHeartbeat = millis();
    uint8_t packet[6];
    packet[0] = CMD_HEARTBEAT;
    packet[1] = 4;
    pack_u32(clientID, packet + 2);
    sendUdp(packet, sizeof(packet));
  }

  pollUdp();

  delay(2);
}


void processUdpPacket(uint8_t *buf, size_t len) {
  // Must have at least CMD + LEN
  if (len < 2) {
    Serial.println(F("UDP: packet too short"));
    return;
  }

  uint8_t cmd = buf[0];
  uint8_t payloadLen = buf[1];

  if (payloadLen + 2 != len) {
    Serial.print(F("UDP: length mismatch, expected "));
    Serial.print(payloadLen + 2);
    Serial.print(F(" got "));
    Serial.println(len);
    return;
  }

  handlePacket(cmd, buf + 2, payloadLen);
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

// Write the current step to the lamps
ICACHE_RAM_ATTR void showStep() {
  digitalWrite(GREEN, getBit(program[step], 0));
  digitalWrite(YELLOW, getBit(program[step], 1));
  digitalWrite(RED, getBit(program[step], 2));
}

ICACHE_RAM_ATTR void blink() {
  if (length < 1) return;
  if (++step >= length) {
    step = 0;
  }
  showStep();
}

// Lock to the app's show clock. Payload: tempo (u8), step counter (u32), phase within step (u16, 0..65535).
// Every unit gets the same broadcast, so units stay in step with each other even if the
// network delays the beacon. Small errors are corrected gradually; big ones (and the first
// beacon after a program swap or tempo change) jump straight to the target.
void applySync(uint8_t *p) {
  uint8_t newTempo = p[0] < 1 ? 1 : p[0];
  uint32_t k = ((uint32_t)p[1] << 24) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 8) | p[4];
  uint16_t frac = ((uint16_t)p[5] << 8) | p[6];

  noInterrupts();
  int len = length;
  if (len < 1) {
    interrupts();
    return;
  }

  bool jump = hardSync || newTempo != tempo;
  tempo = newTempo;
  int32_t M = (int32_t)len * newTempo;
  int32_t cur = ((int32_t)step * newTempo + ticks) % M;
  int32_t target = (int32_t)(k % len) * newTempo + (int32_t)(((uint32_t)frac * newTempo) >> 16);

  int32_t err = target - cur;
  if (err > M / 2) err -= M;
  else if (err < -M / 2) err += M;

  int32_t pos;
  if (jump || abs(err) > SYNC_JUMP_TICKS) {
    pos = target;
    hardSync = false;
  } else {
    pos = cur + err / 2;
  }
  pos = ((pos % M) + M) % M;

  int newStep = pos / newTempo;
  ticks = pos % newTempo;
  if (newStep != step) {
    step = newStep;
    showStep();
  }
  interrupts();

#if SYNC_DEBUG
  Serial.printf("sync k=%lu tempo=%u err=%ld ticks %s\n", (unsigned long)k, newTempo, (long)err, jump || abs(err) > SYNC_JUMP_TICKS ? "JUMP" : "slew");
#endif
}



void handlePacket(uint8_t cmd, uint8_t *payload, uint8_t len) {
  uint8_t i;
  uint16_t test;

  /*
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
  */

  switch (cmd) {

    case 0x00: // READY
      ready = true;
      length = 1;
      step = 0;
      program[0] = 0b00000100;
      break;

    case 0x01:  // TEMPO
      test = payload[0];

      // safety....
      if (test < 1) test = 1;
      else if (test > 255) test = 256;
      
      // keep the position within the step when the speed changes, then lock hard on the next beacon
      noInterrupts();
      ticks = (uint32_t)ticks * test / tempo;
      tempo = test;
      hardSync = true;
      interrupts();
      break;
      
    case 0x02:  // RESET (restart nåværende program)
      noInterrupts();
      step = 0;
      ticks = 0;
      if (length > 0) showStep();
      interrupts();
      break;
  
    case 0x03:  // MOTTA PROGRAM  (dump payload inn i *rec_program)
      //memcpy(rec_program, payload, len);  
      for (i = 0; i < len; i++) {
        rec_program[i] = payload[i];
      }
      rec_length = len;
      break;
    
    case 0x04: // BYTT PROGRAM (bytt om referanser og bruk av *program og *rec_program)
      //swapArrays(program, rec_program);
      noInterrupts();
      for (i = 0; i < rec_length; i++) {
        program[i] = rec_program[i];
      }
      length = rec_length;
      step = 0;  // og RESET!
      ticks = 0;
      if (length > 0) showStep();
      hardSync = true;
      interrupts();
      programSet = true;
      break;

    case 0x05:  // SYNC beacon
      if (len >= 7) applySync(payload);
      break;

    case 0x77: // DISCOVER: app is looking for units
      sendAnnounce();
      break;
      
    default:
      break;
  }
}
