
stop-hadoop:
	cd hadoop && sbin/stop-dfs.sh && sbin/stop-yarn.sh && sbin/mr-jobhistory-daemon.sh stop historyserver

stop-hadoop-secure:
	cd hadoop && sbin/stop-dfs.sh && ./sbin/stop-secure-dns.sh && sbin/stop-yarn.sh && sbin/mr-jobhistory-daemon.sh stop historyserver

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

start-hive-metastore:
	# /usr/bin/mysqld_safe --basedir=/usr 2>&1 1>hive/hive.metastore.mysql.log &
	echo myql -uroot -e "create user 'hive'@'localhost' identified by '123456'; grant all on *.* to hive@'localhost';"
	cd hive && bin/hive --service metastore 2>&1 1>hive.metastore.service.log &

start-hive:
	cd hive && bin/hiveserver2 2>&1 1 > hive.service.log &

stop-hive:
	cd hive && lsof -i:10000 | grep java | awk '{print $$2}' | xargs kill

beeline:
	cd hive && bin/beeline -n root -u jdbc:hive2://localhost:10000/default

beeline-auth:
	kinit -kt /hd/conf/root.keytab root@HADOOP.COM
	echo 'input `!connect jdbc:hive2://__HOST__:10000/default;principal=root/_HOST@HADOOP.COM` in beeline console below and type two `Enter` for username/pass to connect to hive'
	cd hive && bin/beeline

spark-shell:
	cd spark && bin/spark-shell --master yarn -v

start-zk:
	cd zookeeper && bin/zkServer.sh start

stop-zk:
	cd zookeeper && bin/zkServer.sh stop

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


BUILD_HOST=shd

build-docker:
	mkdir -pv files
	test -f files/hadoop-2.7.7.tar.gz || wget https://archive.apache.org/dist/hadoop/common/hadoop-2.7.7/hadoop-2.7.7.tar.gz -O files/hadoop-2.7.7.tar.gz
	test -f files/apache-hive-1.2.2-bin.tar.gz || wget https://mirrors.tuna.tsinghua.edu.cn/apache/hive/hive-1.2.2/apache-hive-1.2.2-bin.tar.gz
	test -f files/mysql-connector-java-5.1.48.jar || wget https://repo1.maven.org/maven2/mysql/mysql-connector-java/5.1.48/mysql-connector-java-5.1.48.jar
	test -f files/apache-zookeeper-3.5.5-bin.tar.gz || wget https://archive.apache.org/dist/zookeeper/zookeeper-3.5.5/apache-zookeeper-3.5.5-bin.tar.gz
	test -f files/hbase-1.3.6-bin.tar.gz || wget http://mirrors.tuna.tsinghua.edu.cn/apache/hbase/hbase-1.3.6/hbase-1.3.6-bin.tar.gz
	test -f files/spark-2.1.0-bin-hadoop2.7.tgz || wget https://archive.apache.org/dist/spark/spark-2.1.0/spark-2.1.0-bin-hadoop2.7.tgz
	docker build -t bigdata-auth . --build-arg build_host=${BUILD_HOST}






