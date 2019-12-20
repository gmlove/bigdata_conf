#!/bin/bash

set -e

/usr/sbin/sshd
sleep 1

/usr/sbin/krb5kdc && /usr/sbin/kadmind
sleep 1

cd /hd/

IP=$(ifconfig eth0 | grep inet | cut -c14- | cut -d' ' -f1)
ssh-keyscan -H `hostname`,$IP >> ~/.ssh/known_hosts
ssh-keyscan -H $IP >> ~/.ssh/known_hosts
ssh-keyscan -H 0.0.0.0 >> ~/.ssh/known_hosts
ssh-keyscan -H `hostname` >> ~/.ssh/known_hosts
ssh-keyscan -H localhost >> ~/.ssh/known_hosts
ssh-keyscan -H localhost,127.0.0.1 >> ~/.ssh/known_hosts

export BIGDATA_BASE=/hd
export JAVA_HOME=/usr/lib/jvm/java
export HADOOP_HOME=${BIGDATA_BASE}/hadoop
export HADOOP_CONF_DIR=${BIGDATA_BASE}/hadoop/etc/hadoop
export HIVE_HOME=${BIGDATA_BASE}/hive
export SPARK_HOME=${BIGDATA_BASE}/spark
export LIVY_HOME=${BIGDATA_BASE}/livy
export HBASE_HOME=${BIGDATA_BASE}/hbase

export PATH=${HADOOP_HOME}/bin:${HIVE_HOME}/bin:${SPARK_HOME}/bin:${LIVY_HOME}/bin:${HBASE_HOME}/bin:${PATH}


cat >> /root/.bashrc <<EOF
export BIGDATA_BASE=/hd
export JAVA_HOME=/usr/lib/jvm/java
export HADOOP_HOME=${BIGDATA_BASE}/hadoop
export HADOOP_CONF_DIR=${BIGDATA_BASE}/hadoop/etc/hadoop
export HIVE_HOME=${BIGDATA_BASE}/hive
export SPARK_HOME=${BIGDATA_BASE}/spark
export LIVY_HOME=${BIGDATA_BASE}/livy
export HBASE_HOME=${BIGDATA_BASE}/hbase

export PATH=${HADOOP_HOME}/bin:${HIVE_HOME}/bin:${SPARK_HOME}/bin:${LIVY_HOME}/bin:${HBASE_HOME}/bin:${PATH}
EOF

echo "$(tail -n 1 /etc/hosts | cut -d' ' -f1) __HOST__ kdc-server kdc-server.hadoop.com" | tee -a /etc/hosts
make start-hadoop-secure

(/usr/bin/mysqld_safe --basedir=/usr 2>&1 1>hive/hive.metastore.mysql.log &) && sleep 2
make start-hive-metastore
make start-hive

make start-zk
make start-hbase

make start-livy

while true; do sleep 60; done

