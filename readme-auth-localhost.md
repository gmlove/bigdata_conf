
```bash
host_id=8
docker run -d --restart=always -p 12${host_id}22:22 --name gmliao-host-hd01-${host_id} -h hd01-${host_id} \
    -v /data/`hostname`/gmliao:/data -v /data/`hostname`/gmliao/home:/root gmliao-host-hd
# on docker machine
yum install mariadb-server make apache-commons-daemon-jsvc.x86_64 krb5-server krb5-libs krb5-workstation -y
add auth-localhost/krb5.conf /etc/krb5.conf
add auth-localhost/kdc.conf /var/kerberos/krb5kdc/kdc.conf

# create common keys
kadmin.local addprinc -randkey root/localhost@HADOOP.COM
kadmin.local addprinc -randkey HTTP/localhost@HADOOP.COM
kadmin.local xst -k /hd/conf/hadoop.keytab root/localhost@HADOOP.COM HTTP/localhost@HADOOP.COM
kadmin.local addprinc -randkey root@HADOOP.COM
kadmin.local xst -k /hd/conf/root.keytab root@HADOOP.COM

#---------- hive
# init/start mysql
/usr/libexec/mariadb-prepare-db-dir mariadb.service
/usr/bin/mysqld_safe --basedir=/usr 2>&1 1>hive/hive.metastore.mysql.log &
mysql -uroot -e "create user 'hive'@'localhost' identified by '123456'; grant all on *.* to hive@'localhost';"

#---------- hbase
# init zk
kadmin.local addprinc -randkey zookeeper/localhost@HADOOP.COM
kadmin.local xst -k /hd/conf/zookeeper.keytab zookeeper/localhost@HADOOP.COM

#---------- livy


#---------- test
# kdc
ssh -L localhost:1802:localhost:1802 root@rhd01-8

## test hbase:
# hbase zookeeper
ssh -L localhost:2181:localhost:2181 root@rhd01-8
# hbase master
ssh -L localhost:16000:localhost:16000 root@rhd01-8
# hbase region server
ssh -L localhost:16201:localhost:16201 root@rhd01-8

## test hive:
# hive
ssh -L localhost:10000:localhost:10000 root@rhd01-8

## test hdfs:
# hdfs namenode
ssh -L localhost:9000:localhost:9000 root@rhd01-8
# hdfs datanode
sudo ssh -i /Users/gmliao/.ssh/id_rsa -L localhost:1004:localhost:1004 root@rhd01-8 -p 12822

## test livy
ssh -L localhost:8998:localhost:8998 root@rhd01-8

## test spark with hive
# hdfs namenode
ssh -L localhost:9000:localhost:9000 root@rhd01-8
# hdfs datanode
sudo ssh -i /Users/gmliao/.ssh/id_rsa -L localhost:1004:localhost:1004 root@rhd01-8 -p 12822
# hive metastore
ssh -L localhos9083:localhost:9083 root@rhd01-8
```
