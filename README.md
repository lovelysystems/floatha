# floatha - Floating IP Assigner

This projects provides the possibility to make a http/s service on digital ocean highly
available by using a floating ip and digital ocean tags.

# Prerequisites

- A set of droplets providing a health endpoint on a private IP returning 200 as status code
- All droplets have a common tag to be used for discovery
- A floating IP address, probably assigned already to one of the droplets.
- A Digital Ocean access token, see https://www.digitalocean.com/docs/api/create-personal-access-token/

# How it works

The service checks if the floating ip is already assigned to a droplet and does a health
check against the assigned droplet. If the health check fails another droplet from the tagged
ones is looked up and if the health check works against that droplet the floating ip gets assigned
to it.

The application is intended to run inside a docker container and can be configured via the environment
variables described below.

### FLOATING_HEALTH_URI

The health uri to use for checking readiness of the services. Note that the host used in
this URI needs to be the floating ip address e.g: `http://1.1.1.1:8080/ping`

The actual health checks however are done against the internal private ip addresses of
the droplets - not the floating ip. This allows us to use internal health endpoints and also
to check the droplet before assigning the IP.

### DROPLET_TAG

The tag to use to find candidate droplets providing the service. See also
https://www.digitalocean.com/docs/droplets/how-to/tag/

### CHECK_WAIT_TIME

Time to wait in millis after a check before the next check request is made,
defaults to "1000"

### RETRY_WAIT_TIME

Time to wait in millis after a failed health check before the next try starts,
defaults to "1000"

### NUM_RETRIES:

Number of retries done if a halth ceck failed before considering the endpoint as
down, defualts to 5.

## Access Token

The digital ocean access token is taken from the default doctl configuration file as described
under https://github.com/digitalocean/doctl#configuring-default-values.

The `DIGITALOCEAN_ACCESS_TOKEN` env variable is NOT supported for security reasons.

See also the localdev/docker-compose.yml file for an example on how to configure the service.

