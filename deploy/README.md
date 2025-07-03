# Resident Services

## Deployment in K8 cluster with other MOSIP services:
### Pre-requisites
* Set KUBECONFIG variable to point to existing K8 cluster kubeconfig file:
    ```
    export KUBECONFIG=~/.kube/<k8s-cluster.config>
    ```
### Install Resident Module
 ```
    $ ./install.sh
   ```
### Delete
  ```
    $ ./delete.sh
   ```
### Restart
  ```
    $ ./restart.sh
   ```
### Install Keycloak client
  ```
    cd deploy/keycloak
    $ ./keycloak_init.sh
   ```

### Install Apitestrig
```
    cd deploy/apitest-masterdata
    $ ./install.sh
```
* During the execution of the `install.sh` script, a prompt appears requesting information regarding the presence of a public domain and a valid SSL certificate on the server.
* If the server lacks a public domain and a valid SSL certificate, it is advisable to select the `n` option. Opting it will enable the `init-container` with an `emptyDir` volume and include it in the deployment process.
* The init-container will proceed to download the server's self-signed SSL certificate and mount it to the specified location within the container's Java keystore (i.e., `cacerts`) file.
* This particular functionality caters to scenarios where the script needs to be employed on a server utilizing self-signed SSL certificates.
