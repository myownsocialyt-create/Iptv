import { test, describe, before, after, beforeEach } from 'node:test';
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} from '@firebase/rules-unit-testing';
import {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  serverTimestamp,
  Timestamp
} from 'firebase/firestore';
import { readFileSync } from 'node:fs';

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-hypnotix-iptv',
    firestore: {
      rules: readFileSync('firestore.rules', 'utf8'),
      host: '127.0.0.1',
      port: 8085
    }
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

describe('Notifications Security Rules', () => {
  test('Authenticated user can create and read their own notification', async () => {
    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    const notifRef = doc(aliceDb, 'notifications', 'notif_1');
    await assertSucceeds(
      setDoc(notifRef, {
        userId: 'alice',
        audience: 'all',
        title: 'Match Live Now!',
        message: 'Watch IPL Live in Full HD on Channel 104.',
        imageUrl: 'https://example.com/banner.jpg',
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );
    await assertSucceeds(getDoc(notifRef));
  });

  test('Public broadcast notification with admin_broadcast can be created and read', async () => {
    const unauthDb = testEnv.unauthenticatedContext().firestore();
    const notifRef = doc(unauthDb, 'notifications', 'notif_broadcast_1');
    await assertSucceeds(
      setDoc(notifRef, {
        userId: 'admin_broadcast',
        audience: 'all',
        title: 'New Channels Added',
        message: '50+ HD Sports & Movie channels are now live!',
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );
    await assertSucceeds(getDoc(notifRef));
  });

  test('Private notification cannot be read by another user or unauthenticated user', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const adminDb = context.firestore();
      await setDoc(doc(adminDb, 'notifications', 'private_1'), {
        userId: 'alice',
        audience: 'private',
        title: 'Private Alert',
        message: 'Subscription renewal reminder',
        createdAt: Timestamp.now(),
        updatedAt: Timestamp.now()
      });
    });

    const bobDb = testEnv.authenticatedContext('bob').firestore();
    await assertFails(getDoc(doc(bobDb, 'notifications', 'private_1')));

    const unauthDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(unauthDb, 'notifications', 'private_1')));
  });

  test('Rejects oversized title or extra unwhitelisted fields', async () => {
    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    const notifRef = doc(aliceDb, 'notifications', 'notif_bad');

    await assertFails(
      setDoc(notifRef, {
        userId: 'alice',
        audience: 'all',
        title: 'A'.repeat(150),
        message: 'Valid message',
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );

    await assertFails(
      setDoc(notifRef, {
        userId: 'alice',
        audience: 'all',
        title: 'Valid Title',
        message: 'Valid message',
        isAdminHack: true,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );
  });

  test('Non-owner cannot update or delete notification', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const adminDb = context.firestore();
      await setDoc(doc(adminDb, 'notifications', 'notif_alice'), {
        userId: 'alice',
        audience: 'all',
        title: 'Original Title',
        message: 'Original message',
        createdAt: Timestamp.now(),
        updatedAt: Timestamp.now()
      });
    });

    const bobDb = testEnv.authenticatedContext('bob').firestore();
    await assertFails(
      updateDoc(doc(bobDb, 'notifications', 'notif_alice'), {
        title: 'Hacked Title',
        updatedAt: serverTimestamp()
      })
    );
    await assertFails(deleteDoc(doc(bobDb, 'notifications', 'notif_alice')));
  });
});
