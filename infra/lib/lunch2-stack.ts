import { CfnOutput, Stack, StackProps } from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Asset } from 'aws-cdk-lib/aws-s3-assets';
import { Construct } from 'constructs';

export interface Lunch2StackProps extends StackProps {
  /** Directory of the Quarkus fast-jar build (backend/target/quarkus-app, SPA included). */
  appDir: string;
}

/**
 * Cheapest possible public deployment: one free-tier EC2 instance with a fixed Elastic IP.
 * Quarkus serves both the API and the built frontend on port 80, so they share one origin.
 * Data lives in a JSON file on the instance and is lost when the instance is replaced (every deploy).
 * No NAT gateway, load balancer, database or domain – nothing that bills while idle except the public IPv4.
 */
export class Lunch2Stack extends Stack {
  constructor(scope: Construct, id: string, props: Lunch2StackProps) {
    super(scope, id, props);

    const vpc = new ec2.Vpc(this, 'Vpc', {
      maxAzs: 1,
      natGateways: 0,
      subnetConfiguration: [{ name: 'public', subnetType: ec2.SubnetType.PUBLIC }],
    });

    const securityGroup = new ec2.SecurityGroup(this, 'WebSg', {
      vpc,
      description: 'Lunch2: HTTP from anywhere, no SSH (use SSM Session Manager)',
    });
    securityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(80), 'HTTP');

    const eip = new ec2.CfnEIP(this, 'PublicIp', { tags: [{ key: 'Name', value: 'lunch2' }] });

    const app = new Asset(this, 'AppBundle', { path: props.appDir });

    const userData = ec2.UserData.forLinux();
    userData.addCommands(
      'set -euo pipefail', // no -x: secrets would land in the EC2 console log
      'dnf install -y java-21-amazon-corretto-headless unzip',
      'useradd --system --home-dir /opt/lunch2 --shell /sbin/nologin lunch2 || true',
      'mkdir -p /opt/lunch2/app /opt/lunch2/data',
    );
    const zipPath = userData.addS3DownloadCommand({ bucket: app.bucket, bucketKey: app.s3ObjectKey });
    userData.addCommands(
      `unzip -o ${zipPath} -d /opt/lunch2/app`,
      // Secrets are put into SSM Parameter Store by scripts/deploy.sh, never into this template.
      `REGION=${this.region}`,
      'ADMIN_KEY=$(aws ssm get-parameter --region $REGION --name /lunch2/admin-key --with-decryption --query Parameter.Value --output text)',
      'AI_KEY=$(aws ssm get-parameter --region $REGION --name /lunch2/anthropic-api-key --with-decryption --query Parameter.Value --output text 2>/dev/null || true)',
      'cat > /opt/lunch2/env <<EOF',
      'QUARKUS_HTTP_PORT=80',
      'LUNCH2_DATA_FILE=/opt/lunch2/data/lunch2.json',
      `LUNCH2_PUBLIC_BASE_URL=http://${eip.attrPublicIp}`,
      'LUNCH2_DEMO_MODE=true',
      'LUNCH2_ADMIN_KEY=$ADMIN_KEY',
      'ANTHROPIC_API_KEY=$AI_KEY',
      'EOF',
      'chmod 600 /opt/lunch2/env',
      'chown -R lunch2:lunch2 /opt/lunch2',
      'cat > /etc/systemd/system/lunch2.service <<"EOF"',
      '[Unit]',
      'Description=Lunch2 (Quarkus API + frontend)',
      'After=network-online.target',
      '[Service]',
      'User=lunch2',
      'EnvironmentFile=/opt/lunch2/env',
      'AmbientCapabilities=CAP_NET_BIND_SERVICE',
      'WorkingDirectory=/opt/lunch2',
      'ExecStart=/usr/bin/java -Xmx384m -Duser.language=en -jar /opt/lunch2/app/quarkus-run.jar',
      'Restart=always',
      '[Install]',
      'WantedBy=multi-user.target',
      'EOF',
      'systemctl daemon-reload',
      'systemctl enable --now lunch2',
    );

    const instance = new ec2.Instance(this, 'Server', {
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      securityGroup,
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MICRO),
      machineImage: ec2.MachineImage.latestAmazonLinux2023(),
      requireImdsv2: true,
      ssmSessionPermissions: true,
      userData,
      // A new build means new user data; replacing the instance is the simplest reliable redeploy.
      userDataCausesReplacement: true,
    });
    app.grantRead(instance.role);
    instance.addToRolePolicy(
      new iam.PolicyStatement({
        actions: ['ssm:GetParameter'],
        resources: [`arn:${this.partition}:ssm:${this.region}:${this.account}:parameter/lunch2/*`],
      }),
    );

    new ec2.CfnEIPAssociation(this, 'PublicIpAssociation', {
      allocationId: eip.attrAllocationId,
      instanceId: instance.instanceId,
    });

    new CfnOutput(this, 'PublicIpAddress', { value: eip.attrPublicIp });
    new CfnOutput(this, 'Url', { value: `http://${eip.attrPublicIp}` });
    new CfnOutput(this, 'ShellCommand', {
      value: `aws ssm start-session --region ${this.region} --target ${instance.instanceId}`,
    });
  }
}
