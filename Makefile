prepare-noauth:
	mkdir noauth
	cd noauth && mkdir livy hive spark hadoop hbase
	cd noauth && \
		scp rhd01-7:dev/projects/bigdata-noauth/bigdata/env ./ && \
		scp rhd01-7:dev/projects/bigdata-noauth/bigdata/Makefile ./ && \
		scp -r rhd01-7:dev/projects/bigdata-noauth/bigdata/hadoop/etc ./hadoop/etc && \
		scp -r rhd01-7:dev/projects/bigdata-noauth/bigdata/hadoop/sbin ./hadoop/sbin && \
		scp -r rhd01-7:dev/projects/bigdata-noauth/bigdata/hbase/conf ./hbase/conf && \
		scp -r rhd01-7:dev/projects/bigdata-noauth/bigdata/hive/conf ./hive/conf && \
		scp -r rhd01-7:dev/projects/bigdata-noauth/bigdata/spark/conf ./spark/conf && \
		scp -r rhd01-7:dev/projects/bigdata-noauth/bigdata/livy/conf ./livy/conf
	cd noauth && \
		touch apache-hive-1.2.2-bin.tar.gz hadoop-2.7.7.tar.gz hbase-1.3.6-bin.tar.gz livy-0.5.0-incubating-bin.zip spark-2.1.0-bin-hadoop2.7.tgz

prepare-auth:
	mkdir auth
	cd auth && mkdir livy hive spark hadoop hbase
	cd auth && \
		scp rhd01-7:dev/projects/bigdata/env ./ && \
		scp rhd01-7:dev/projects/bigdata/Makefile ./ && \
		scp -r rhd01-7:dev/projects/bigdata/hadoop/etc ./hadoop/etc && \
		scp -r rhd01-7:dev/projects/bigdata/hadoop/sbin ./hadoop/sbin && \
		scp -r rhd01-7:dev/projects/bigdata/hbase/conf ./hbase/conf && \
		scp -r rhd01-7:dev/projects/bigdata/hive/conf ./hive/conf && \
		scp -r rhd01-7:dev/projects/bigdata/spark/conf ./spark/conf && \
		scp -r rhd01-7:dev/projects/bigdata/livy/conf ./livy/conf
	cd auth && \
		touch apache-hive-1.2.2-bin.tar.gz hadoop-2.7.7.tar.gz hbase-1.3.6-bin.tar.gz livy-0.5.0-incubating-bin.zip spark-2.1.0-bin-hadoop2.7.tgz


