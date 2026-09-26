# Public test signing key

`test.keystore` signs CI test builds so each one installs as an update over the previous one.
Its passwords are in the workflow on purpose: it is **not** a release key and anyone can sign
with it. Don't distribute APKs signed with it.
