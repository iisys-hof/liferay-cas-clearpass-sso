# liferay-cas-clearpass-sso

Liferay Hook Plugin that configures an automatic CAS ClearPass login, temporarily storing the user's credentials for a session.
Not compatible with Liferay's existing CAS authentication.

Building:
* Import into a Liferay SDK, under hooks, in a folder called "CASClearPass-hook"
* build by executing "ant" int the source folder or using "build jar" in a Liferay SDK Eclipse
* the binary will now be located in the SDK's "dist" folder

Installation:
* "session.store.password=true" has to be set in Liferay's "portal-ext.properties"
* Under Administration - Portal Settings - General, you must set the default logout URL to CAS' logout URL
* Unpack the war and adjust the following configuration files:
* edit /docroot/WEB-INF/src/portal.properties
* edit /docroot/WEB-INF/src/cas_autologin.properties
* WARNING: debug mode will log all passwords to the console
* repackage the war file
* put the war file in Liferay's "deploy" folder and wait for it to be deployed

Usage:
* For any request to Liferay you'll be redirected to CAS for authentication and afterwards you'll be redirected to your original request's URL
* To opt out of the auto-authentication, add the parameter "casOptOut=true" to your request URL


Flow Pseudocode:
> Liferay Redirect -> $CAS_URL/login/($SERVICE,$SERVICE_REDIRECT)

> CAS Redirect -> $SERVICE_REDIRECT($PROXY_TICKET)

> Liferay Call -> $CAS_URL/verify/($SERVICE,$PROXY_TICKET,$PGT_CALLBACK_URL)

>     CAS Call -> $PGT_CALLBACK_URL($PGT_ID,$PGT_IOU)

> CAS Response -> $USER_NAME,$PGT_IOU

> Liferay Call -> $CAS_URL/proxy/($PGT_ID,$CLEARPASS_SERVICE)

> CAS Response -> $PROXY_TICKET

> Liferay Call -> $CAS_URL/clearPass/($PROXY_TICKET)

> CAS Response -> $CLEARTEXT_PASSWORD