package im.status.keycard.build;

import im.status.keycard.applet.Identifiers;
import im.status.keycard.applet.KeycardCommandSet;
import im.status.keycard.desktop.PCSCCardChannel;
import im.status.keycard.globalplatform.GlobalPlatformCommandSet;
import im.status.keycard.globalplatform.LoadCallback;
import im.status.keycard.io.APDUException;
import org.bouncycastle.util.encoders.Hex;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;

import javax.smartcardio.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.SecureRandom;

import apdu4j.pcsc.TerminalManager;

public class LockTask extends DefaultTask {

  @TaskAction
  public void install() {
    Logger logger = getLogger();

    TerminalFactory tf = TerminalManager.getTerminalFactory();
    CardTerminal cardTerminal = null;

    try {
      for (CardTerminal t : tf.terminals().list()) {
        if (t.isCardPresent()) {
          cardTerminal = t;
          break;
        }
      }
    } catch(CardException e) {
      throw new GradleException("Error listing card terminals", e);
    }

    if (cardTerminal == null) {
      throw new GradleException("No available PC/SC terminal");
    }

    Card apduCard;

    try {
      apduCard = cardTerminal.connect("*");
    } catch(CardException e) {
      throw new GradleException("Couldn't connect to the card", e);
    }

    logger.info("Connected to " + cardTerminal.getName());
    PCSCCardChannel sdkChannel = new PCSCCardChannel(apduCard.getBasicChannel());
    GlobalPlatformCommandSet cmdSet = new GlobalPlatformCommandSet(sdkChannel);
    KeycardCommandSet keycardSet = new KeycardCommandSet(sdkChannel);

    try {
      logger.info("Selecting Keycard applet (don't lock if not installed)");
      keycardSet.select().checkOK();

      logger.info("Selecting the ISD");
      cmdSet.select().checkOK();
      logger.info("Opening a SecureChannel");
      cmdSet.openSecureChannel(false);
      logger.info("Changing SCP02 keys to random ones");
      SecureRandom rand = new SecureRandom();
      byte[] enck = new byte[16];
      rand.nextBytes(enck);
      byte[] mack = new byte[16];
      rand.nextBytes(mack);
      byte[] deck = new byte[16];
      rand.nextBytes(deck);
      cmdSet.putSCP02Keys(enck, mack, deck, 0, 1).checkOK();
    } catch (IOException e) {
      throw new GradleException("I/O error", e);
    } catch (APDUException e) {
      throw new GradleException(e.getMessage(), e);
    }
  }
}
