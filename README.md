# influxdb-proxy

Utility for visualizing the effect of Riemann's `ewma-timelss` stream on a time
series that already exists in InfluxDB.

## Usage

Modify `src/influxdb_proxy/core.clj` to point to your influxdb server.  Download
Grafana and point it to `localhost:8080/db/<db_name>`, where `db_name` is an
existing database on your InfluxDB server.

Then:

    lein run

Open up Grafana and graph yourself some metrics.  Adjust the `halflife` argument
to `ewma-timeless` to see the effect of different values on the moving average.
