# liferay-cas-clearpass-sso
Liferay CAS SSO plugin with ClearPass support that logs in users automatically. It uses CAS ClearPass to retrieve the user's password so it can be used by CMIS for example.

not compatible with Liferay's existing CAS support

The Liferay 7 GA2 version is an OSGi module that uses a JAX-RS Webservice for the Callback. https://dev.liferay.com/develop/tutorials/-/knowledge_base/7-0/jax-ws-and-jax-rs

Building:
* Import into a Liferay 7 GA2 IDE as Liferay Module
* Build using Gradle
* The Binary will be in the folder "build/libs/"

Installation:
* "session.store.password=true" has to be set in Liferay's "portal-ext.properties"
* Under Administration - Portal Settings - General, you must set the default logout URL to CAS' logout URL
* perform the "DXP Configuration" as described here: https://github.com/xtivia/dxp-rest-example
* IMPORTANT: disable any existing Liferay CAS configuration, since it will interfere with the plugin
* Unpack the jar and adjust the following configuration files:
* edit /src/main/resources/cas_autologin.properties
* edit /src/main/resources/portal.properties
* WARNING: debug mode will log all passwords to the console
* repackage the jar file
* put the jar file in Liferay's "deploy" folder and wait for it to be deployed

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