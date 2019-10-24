
stop-hadoop:
	cd hadoop && sbin/stop-dfs.sh && sbin/stop-yarn.sh && sbin/mr-jobhistory-daemon.sh stop historyserver

start-hadoop-secure:
	cd hadoop && sbin/start-dfs.sh && ./sbin/start-secure-dns.sh && sbin/start-yarn.sh && sbin/mr-jobhistory-daemon.sh start historyserver

start-hadoop:
	cd hadoop && sbin/start-dfs.sh && sbin/start-yarn.sh && sbin/mr-jobhistory-daemon.sh start historyserver

test-hadoop:
	cd hadoop; bin/hadoop dfs -rm -r output;  bin/hadoop jar share/hadoop/mapreduce/hadoop-mapreduce-examples-2.7.7.jar grep input output 'dfs[a-z.]+'; bin/hdfs dfs -cat output/*

test-spark:
	cd spark; ./bin/spark-submit   \
    		--class org.apache.spark.examples.SparkPi \
 		--master yarn   --deploy-mode cluster \
	   	--executor-memory 512M   --num-executors 1 \
    		examples/jars/spark-examples_2.11-2.1.0.jar 1000

start-hive:
	cd hive && bin/hiveserver2 2>&1 1 > hive.service.log &

stop-hive:
	cd hive && lsof -i:10000 | grep java | awk '{print $$2}' | xargs kill

beeline:
	cd hive && bin/beeline -n root -u jdbc:hive2://localhost:10000/default

spark-shell:
	cd spark && bin/spark-shell --master yarn -v

start-hbase:
	cd hbase && bin/start-hbase.sh

stop-hbase:
	cd hbase && bin/stop-hbase.sh

hbase-shell:
	cd hbase && bin/hbase shell

start-livy:
	cd livy && ./bin/livy-server start

stop-livy:
	cd livy && ./bin/livy-server stop
