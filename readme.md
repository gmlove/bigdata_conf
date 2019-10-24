ALL in one


https://archive.apache.org/dist/hadoop/common/hadoop-2.7.7/hadoop-2.7.7.tar.gz
https://archive.apache.org/dist/spark/spark-2.1.0/spark-2.1.0-bin-hadoop2.7.tgz
https://mirrors.tuna.tsinghua.edu.cn/apache/hive/hive-1.2.2/apache-hive-1.2.2-bin.tar.gz
https://www.apache.org/dyn/closer.lua/hbase/hbase-1.3.6/hbase-1.3.6-bin.tar.gz
https://archive.apache.org/dist/incubator/livy/0.5.0-incubating/livy-0.5.0-incubating-bin.zip

```
yum install krb5-server krb5-libs krb5-workstation -y
## make sync-krb5-allinone
kdb5_util create -r HADOOP.COM -s  # KDC database master key: 123456
/usr/sbin/krb5kdc && /usr/sbin/kadmind
echo "$(ifconfig | grep 172 | awk '{print $2}') hd01-7.hadoop.com"

kadmin.local addprinc root/admin
kadmin.local addprinc gml/admin
kadmin.local ktadd -k /var/kerberos/krb5kdc/kadm5.keytab gml/admin@HADOOP.COM
kinit -kt /var/kerberos/krb5kdc/kadm5.keytab gml/admin@HADOOP.COM

kadmin.local addprinc -randkey root/hd01-7@HADOOP.COM && \
kadmin.local addprinc -randkey HTTP/hd01-7@HADOOP.COM && \
mkdir -pv /root/dev/projects/bigdata/hadoop/data/hd01-7/conf && \
kadmin.local xst -k /root/dev/projects/bigdata/hadoop/data/hd01-7/conf/hadoop.keytab root/hd01-7 HTTP/hd01-7
```


