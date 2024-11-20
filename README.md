This is small tool for help measure delay of appearance process instances in Camunda Operate.

## REST-API
| point                                           | Description                                                                                   |
|-------------------------------------------------|-----------------------------------------------------------------------------------------------|
| `PUT /api/monitor/{processDefinitionKey}/start` | Start to monitor appearance new process instances in Operate with provided process definition |
| `PUT /api/monitor/{processDefinitionKey}/stop`  | Stop monitor                                                                                  |
| `PUT /api/keys/{nextProcessInstanceKey}`        | Add new process instance ID for measure delay                                                 |
| `GET /api/stats/remaining`                      | Get current remaining amount of not found process instances                                   |
